package com.jiake.jk.video.service;

import com.jiake.jk.video.constant.RabbitMQConstant;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FollowFeedDeadLetterRecoveryServiceTest {

    @Test
    void recover_shouldAckAfterAuditAndThenSendNewOutbox() throws Exception {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        com.rabbitmq.client.Channel channel = mock(com.rabbitmq.client.Channel.class);
        FollowFeedDeadLetterPreparationService preparationService = mock(FollowFeedDeadLetterPreparationService.class);
        OutboxMessagePublisher publisher = mock(OutboxMessagePublisher.class);
        GetResponse response = deadLetter(701L);
        when(channel.basicGet(RabbitMQConstant.VIDEO_PUBLISH_INBOX_DEAD_QUEUE, false))
                .thenReturn(response)
                .thenReturn(null);
        when(preparationService.prepare(any())).thenReturn(new FollowFeedDeadLetterPreparationService.RecoveryPreparation(
                9001L, true, 501L, "trace-recover"));
        executeOn(rabbitTemplate, channel);

        FollowFeedDeadLetterRecoveryService.RecoveryResult result = new FollowFeedDeadLetterRecoveryService(
                rabbitTemplate, preparationService, publisher, new SimpleMeterRegistry()).recover(1);

        assertEquals(new FollowFeedDeadLetterRecoveryService.RecoveryResult(1, 0), result);
        var order = inOrder(preparationService, channel, publisher);
        order.verify(preparationService).prepare(any());
        order.verify(channel).basicAck(701L, false);
        order.verify(publisher).publish(9001L);
    }

    @Test
    void recover_shouldNotNackAcknowledgedMessageWhenImmediateSendFails() throws Exception {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        com.rabbitmq.client.Channel channel = mock(com.rabbitmq.client.Channel.class);
        FollowFeedDeadLetterPreparationService preparationService = mock(FollowFeedDeadLetterPreparationService.class);
        OutboxMessagePublisher publisher = mock(OutboxMessagePublisher.class);
        GetResponse response = deadLetter(702L);
        when(channel.basicGet(RabbitMQConstant.VIDEO_PUBLISH_INBOX_DEAD_QUEUE, false)).thenReturn(response);
        when(preparationService.prepare(any())).thenReturn(new FollowFeedDeadLetterPreparationService.RecoveryPreparation(
                9002L, true, 502L, "trace-send-failed"));
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).publish(9002L);
        executeOn(rabbitTemplate, channel);

        FollowFeedDeadLetterRecoveryService service = new FollowFeedDeadLetterRecoveryService(
                rabbitTemplate, preparationService, publisher, new SimpleMeterRegistry());

        assertThrows(IllegalStateException.class, () -> service.recover(1));
        verify(channel).basicAck(702L, false);
        verify(channel, never()).basicNack(eq(702L), eq(false), eq(true));
    }

    private void executeOn(RabbitTemplate rabbitTemplate, com.rabbitmq.client.Channel channel) {
        when(rabbitTemplate.execute(any(ChannelCallback.class))).thenAnswer(invocation -> {
            ChannelCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(channel);
        });
    }

    private GetResponse deadLetter(long deliveryTag) {
        GetResponse response = mock(GetResponse.class);
        Envelope envelope = mock(Envelope.class);
        when(response.getBody()).thenReturn("{\"videoId\":501,\"creatorId\":601,\"publishedAt\":\"2026-08-14T12:00:00\",\"traceId\":\"dead-letter-test\"}".getBytes());
        when(response.getEnvelope()).thenReturn(envelope);
        when(envelope.getDeliveryTag()).thenReturn(deliveryTag);
        return response;
    }
}
