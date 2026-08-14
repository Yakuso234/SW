package com.jiake.jk.video.consumer;

import com.jiake.jk.video.pojo.mq.VideoPublishedMessage;
import com.jiake.jk.video.service.FollowFeedService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class VideoPublishedInboxConsumerTest {

    @Test
    void consume_shouldFanoutOnceWhenDependencyIsAvailable() {
        FollowFeedService followFeedService = mock(FollowFeedService.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        VideoPublishedInboxConsumer consumer = new VideoPublishedInboxConsumer(followFeedService, rabbitTemplate);
        VideoPublishedMessage message = message();

        assertDoesNotThrow(() -> consumer.consume(message, null));

        verify(followFeedService).fanoutPublishedVideo(message);
        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(), any(MessagePostProcessor.class));
    }

    @Test
    void consume_shouldMoveToBrokerRetryQueueWhenAttemptBudgetRemains() {
        FollowFeedService followFeedService = mock(FollowFeedService.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        VideoPublishedInboxConsumer consumer = new VideoPublishedInboxConsumer(followFeedService, rabbitTemplate);
        VideoPublishedMessage message = message();
        doThrow(new IllegalStateException("user service unavailable"))
                .when(followFeedService).fanoutPublishedVideo(message);

        assertDoesNotThrow(() -> consumer.consume(message, 1));

        verify(rabbitTemplate).convertAndSend(eq(""), eq("video.publish.inbox.retry.queue"), eq(message),
                any(MessagePostProcessor.class));
    }

    @Test
    void consume_shouldRejectForFinalDeadLetterAfterAttemptBudgetIsExhausted() {
        FollowFeedService followFeedService = mock(FollowFeedService.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        VideoPublishedInboxConsumer consumer = new VideoPublishedInboxConsumer(followFeedService, rabbitTemplate);
        VideoPublishedMessage message = message();
        doThrow(new IllegalStateException("user service unavailable"))
                .when(followFeedService).fanoutPublishedVideo(message);

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.consume(message, 2));

        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(), any(MessagePostProcessor.class));
    }

    private VideoPublishedMessage message() {
        VideoPublishedMessage message = new VideoPublishedMessage();
        message.setVideoId(1001L);
        message.setCreatorId(2001L);
        message.setTraceId("publish-inbox-test-trace");
        return message;
    }
}
