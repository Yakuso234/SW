package com.jiake.jk.video.service;

import com.jiake.jk.video.constant.RabbitMQConstant;
import com.rabbitmq.client.GetResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/** Operator-only recovery: durable audit/outbox first, then acknowledge the original dead letter. */
@Slf4j
@Service
public class FollowFeedDeadLetterRecoveryService {

    private static final int MAX_BATCH_SIZE = 50;
    private final RabbitTemplate rabbitTemplate;
    private final FollowFeedDeadLetterPreparationService preparationService;
    private final OutboxMessagePublisher outboxMessagePublisher;
    private final Counter recoveryCounter;

    public FollowFeedDeadLetterRecoveryService(RabbitTemplate rabbitTemplate,
                                                FollowFeedDeadLetterPreparationService preparationService,
                                                OutboxMessagePublisher outboxMessagePublisher,
                                                MeterRegistry meterRegistry) {
        this.rabbitTemplate = rabbitTemplate;
        this.preparationService = preparationService;
        this.outboxMessagePublisher = outboxMessagePublisher;
        this.recoveryCounter = Counter.builder("sw.video.publish.inbox.dead.letter.recovery")
                .description("Follow-feed dead letters prepared for replay through the outbox")
                .register(meterRegistry);
    }

    public RecoveryResult recover(int requestedBatchSize) {
        int batchSize = Math.max(1, Math.min(requestedBatchSize, MAX_BATCH_SIZE));
        return rabbitTemplate.execute(channel -> {
            int prepared = 0;
            int alreadyPrepared = 0;
            for (int index = 0; index < batchSize; index++) {
                GetResponse response = channel.basicGet(RabbitMQConstant.VIDEO_PUBLISH_INBOX_DEAD_QUEUE, false);
                if (response == null) break;
                FollowFeedDeadLetterPreparationService.RecoveryPreparation recovery;
                try {
                    String body = new String(response.getBody(), StandardCharsets.UTF_8);
                    recovery = preparationService.prepare(body);
                    channel.basicAck(response.getEnvelope().getDeliveryTag(), false);
                } catch (RuntimeException exception) {
                    channel.basicNack(response.getEnvelope().getDeliveryTag(), false, true);
                    throw exception;
                }
                if (recovery.created()) {
                    prepared++;
                    recoveryCounter.increment();
                    // The audit and PENDING Outbox are already durable. A transient immediate-send error
                    // is retried by OutboxMessagePublisher's scheduled scan; never NACK an acknowledged DLQ item.
                    outboxMessagePublisher.publish(recovery.outboxId());
                } else {
                    alreadyPrepared++;
                }
                log.info("Follow-feed dead letter recovery prepared, videoId={}, traceId={}, created={}",
                        recovery.videoId(), recovery.traceId(), recovery.created());
            }
            return new RecoveryResult(prepared, alreadyPrepared);
        });
    }

    public record RecoveryResult(int prepared, int alreadyPrepared) { }
}
