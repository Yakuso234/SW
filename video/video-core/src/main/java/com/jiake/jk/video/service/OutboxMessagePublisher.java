package com.jiake.jk.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jiake.jk.video.mapper.MessageOutBoxMapper;
import com.jiake.jk.video.pojo.entity.MessageOutbox;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息表投递器：只允许状态被原子抢占为 SENDING 的记录发送到 MQ。
 */
@Slf4j
@Service
public class OutboxMessagePublisher {

    private static final int MAX_RETRY_COUNT = 5;
    private static final int BATCH_SIZE = 100;
    private final MessageOutBoxMapper messageOutBoxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final Timer deliveryTimer;
    private final Counter deliveryFailureCounter;

    public OutboxMessagePublisher(MessageOutBoxMapper messageOutBoxMapper, RabbitTemplate rabbitTemplate,
                                  MeterRegistry meterRegistry) {
        this.messageOutBoxMapper = messageOutBoxMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.deliveryTimer = Timer.builder("sw.outbox.delivery")
                .description("Outbox message delivery attempt duration")
                .register(meterRegistry);
        this.deliveryFailureCounter = Counter.builder("sw.outbox.delivery.failures")
                .description("Outbox delivery failures")
                .register(meterRegistry);
    }

    @PostConstruct
    void registerCallbacks() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (correlationData == null) {
                return;
            }
            Long outboxId = Long.valueOf(correlationData.getId());
            if (ack) {
                markSuccess(outboxId);
            } else {
                markRetryOrDead(outboxId, cause);
            }
        });
        rabbitTemplate.setReturnsCallback(returned -> {
            Object outboxId = returned.getMessage().getMessageProperties().getHeaders().get("x-outbox-id");
            if (outboxId != null) {
                markRetryOrDead(Long.valueOf(outboxId.toString()), returned.getReplyText());
            }
        });
    }

    public void publish(Long outboxId) {
        LocalDateTime now = LocalDateTime.now();
        int claimed = messageOutBoxMapper.update(new LambdaUpdateWrapper<MessageOutbox>()
                .set(MessageOutbox::getStatus, MessageOutbox.OutboxStatus.SENDING)
                .eq(MessageOutbox::getId, outboxId)
                .in(MessageOutbox::getStatus, MessageOutbox.OutboxStatus.PENDING, MessageOutbox.OutboxStatus.FAILED)
                .and(wrapper -> wrapper.isNull(MessageOutbox::getNextRetryTime)
                        .or().le(MessageOutbox::getNextRetryTime, now)));
        if (claimed == 0) {
            return;
        }

        MessageOutbox outbox = messageOutBoxMapper.selectById(outboxId);
        if (outbox == null) {
            return;
        }
        try {
            deliveryTimer.record(() -> {
                Message message = MessageBuilder.withBody(outbox.getMessageBody().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setContentEncoding(StandardCharsets.UTF_8.name())
                        .setHeader("x-outbox-id", outboxId.toString())
                        .build();
                rabbitTemplate.send(outbox.getExchangeName() == null ? "" : outbox.getExchangeName(),
                        outbox.getRoutingKey(), message, new CorrelationData(outboxId.toString()));
            });
        } catch (RuntimeException exception) {
            markRetryOrDead(outboxId, exception.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${sw.outbox.scan-delay-ms:5000}")
    public void retryDueMessages() {
        LocalDateTime now = LocalDateTime.now();
        List<MessageOutbox> dueMessages = messageOutBoxMapper.selectList(new LambdaQueryWrapper<MessageOutbox>()
                .select(MessageOutbox::getId)
                .in(MessageOutbox::getStatus, MessageOutbox.OutboxStatus.PENDING, MessageOutbox.OutboxStatus.FAILED)
                .and(wrapper -> wrapper.isNull(MessageOutbox::getNextRetryTime)
                        .or().le(MessageOutbox::getNextRetryTime, now))
                .orderByAsc(MessageOutbox::getCreatedAt)
                .last("LIMIT " + BATCH_SIZE));
        dueMessages.forEach(message -> publish(message.getId()));
    }

    void markSuccess(Long outboxId) {
        messageOutBoxMapper.update(new LambdaUpdateWrapper<MessageOutbox>()
                .set(MessageOutbox::getStatus, MessageOutbox.OutboxStatus.SUCCESS)
                .eq(MessageOutbox::getId, outboxId)
                .eq(MessageOutbox::getStatus, MessageOutbox.OutboxStatus.SENDING));
    }

    void markRetryOrDead(Long outboxId, String reason) {
        MessageOutbox outbox = messageOutBoxMapper.selectById(outboxId);
        if (outbox == null || outbox.getStatus() != MessageOutbox.OutboxStatus.SENDING) {
            return;
        }
        int nextRetryCount = (outbox.getRetryCount() == null ? 0 : outbox.getRetryCount()) + 1;
        MessageOutbox.OutboxStatus nextStatus = nextRetryCount >= MAX_RETRY_COUNT
                ? MessageOutbox.OutboxStatus.DEAD : MessageOutbox.OutboxStatus.FAILED;
        LocalDateTime nextRetryTime = nextStatus == MessageOutbox.OutboxStatus.DEAD
                ? null : LocalDateTime.now().plusSeconds(1L << Math.min(nextRetryCount, 6));
        int updated = messageOutBoxMapper.update(new LambdaUpdateWrapper<MessageOutbox>()
                .set(MessageOutbox::getStatus, nextStatus)
                .set(MessageOutbox::getRetryCount, nextRetryCount)
                .set(MessageOutbox::getNextRetryTime, nextRetryTime)
                .eq(MessageOutbox::getId, outboxId)
                .eq(MessageOutbox::getStatus, MessageOutbox.OutboxStatus.SENDING));
        if (updated == 1) {
            deliveryFailureCounter.increment();
            log.warn("Outbox message {} delivery failed, status={}, reason={}", outboxId, nextStatus, reason);
        }
    }
}
