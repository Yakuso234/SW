package com.jiake.jk.video.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jiake.jk.video.mapper.MessageOutBoxMapper;
import com.jiake.jk.video.pojo.entity.MessageOutbox;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxMessagePublisherTest {

    @Mock private MessageOutBoxMapper messageOutBoxMapper;
    @Mock private RabbitTemplate rabbitTemplate;

    @BeforeAll
    static void initializeMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "OutboxMessagePublisherTest");
        TableInfoHelper.initTableInfo(assistant, MessageOutbox.class);
    }

    @Test
    void publish_shouldClaimMessageBeforeSendingIt() {
        OutboxMessagePublisher publisher = new OutboxMessagePublisher(messageOutBoxMapper, rabbitTemplate);
        MessageOutbox outbox = new MessageOutbox();
        outbox.setId(501L);
        outbox.setExchangeName("");
        outbox.setRoutingKey("video.review.queue");
        outbox.setMessageBody("{\"videoId\":501}");
        outbox.setStatus(MessageOutbox.OutboxStatus.SENDING);
        when(messageOutBoxMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(messageOutBoxMapper.selectById(501L)).thenReturn(outbox);

        publisher.publish(501L);

        ArgumentCaptor<LambdaUpdateWrapper<MessageOutbox>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(messageOutBoxMapper).update(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("status"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(MessageOutbox.OutboxStatus.SENDING));
        verify(rabbitTemplate).send(eq(""), eq("video.review.queue"), any(), any());
    }

    @Test
    void markRetryOrDead_shouldUseBackoffAfterBrokerFailure() {
        OutboxMessagePublisher publisher = new OutboxMessagePublisher(messageOutBoxMapper, rabbitTemplate);
        MessageOutbox outbox = new MessageOutbox();
        outbox.setId(502L);
        outbox.setStatus(MessageOutbox.OutboxStatus.SENDING);
        outbox.setRetryCount(1);
        when(messageOutBoxMapper.selectById(502L)).thenReturn(outbox);
        when(messageOutBoxMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);

        LocalDateTime before = LocalDateTime.now();
        publisher.markRetryOrDead(502L, "broker unavailable");

        ArgumentCaptor<LambdaUpdateWrapper<MessageOutbox>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(messageOutBoxMapper).update(captor.capture());
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(MessageOutbox.OutboxStatus.FAILED));
        assertTrue(captor.getValue().getParamNameValuePairs().values().stream()
                .filter(LocalDateTime.class::isInstance)
                .map(LocalDateTime.class::cast)
                .anyMatch(time -> !time.isBefore(before.plusSeconds(4))));
    }

    @Test
    void markSuccess_shouldOnlyCompleteAClaimedMessage() {
        OutboxMessagePublisher publisher = new OutboxMessagePublisher(messageOutBoxMapper, rabbitTemplate);
        when(messageOutBoxMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);

        publisher.markSuccess(504L);

        ArgumentCaptor<LambdaUpdateWrapper<MessageOutbox>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(messageOutBoxMapper).update(captor.capture());
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(MessageOutbox.OutboxStatus.SUCCESS));
        assertTrue(captor.getValue().getSqlSegment().contains("status"));
    }

    @Test
    void markRetryOrDead_shouldStopAtRetryLimit() {
        OutboxMessagePublisher publisher = new OutboxMessagePublisher(messageOutBoxMapper, rabbitTemplate);
        MessageOutbox outbox = new MessageOutbox();
        outbox.setId(503L);
        outbox.setStatus(MessageOutbox.OutboxStatus.SENDING);
        outbox.setRetryCount(4);
        when(messageOutBoxMapper.selectById(503L)).thenReturn(outbox);
        when(messageOutBoxMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);

        publisher.markRetryOrDead(503L, "broker unavailable");

        ArgumentCaptor<LambdaUpdateWrapper<MessageOutbox>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(messageOutBoxMapper).update(captor.capture());
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(MessageOutbox.OutboxStatus.DEAD));
        assertEquals(5, captor.getValue().getParamNameValuePairs().values().stream()
                .filter(Integer.class::isInstance)
                .map(Integer.class::cast)
                .max(Integer::compareTo)
                .orElseThrow());
    }
}
