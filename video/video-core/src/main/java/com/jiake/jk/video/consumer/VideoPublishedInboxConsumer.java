package com.jiake.jk.video.consumer;

import com.jiake.jk.common.trace.TraceContext;
import com.jiake.jk.video.constant.RabbitMQConstant;
import com.jiake.jk.video.pojo.mq.VideoPublishedMessage;
import com.jiake.jk.video.service.FollowFeedService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class VideoPublishedInboxConsumer {

    private static final String RETRY_COUNT_HEADER = "x-publish-inbox-retry-count";
    private static final int MAX_ATTEMPTS = 3;

    private final FollowFeedService followFeedService;
    private final RabbitTemplate rabbitTemplate;
    private final Counter retryCounter;
    private final Counter deadLetterCounter;

    public VideoPublishedInboxConsumer(FollowFeedService followFeedService, RabbitTemplate rabbitTemplate,
                                      MeterRegistry meterRegistry) {
        this.followFeedService = followFeedService;
        this.rabbitTemplate = rabbitTemplate;
        this.retryCounter = Counter.builder("sw.video.publish.inbox.retry")
                .description("Video publish inbox messages moved to the delayed retry queue")
                .register(meterRegistry);
        this.deadLetterCounter = Counter.builder("sw.video.publish.inbox.dead.letter")
                .description("Video publish inbox messages exhausted retry attempts")
                .register(meterRegistry);
    }

    @RabbitListener(queues = RabbitMQConstant.VIDEO_PUBLISH_INBOX_QUEUE,
            containerFactory = "videoPublishInboxContainerFactory")
    public void consume(VideoPublishedMessage message,
                        @Header(name = RETRY_COUNT_HEADER, required = false) Integer retryCount) {
        try {
            TraceContext.setTraceId(message.getTraceId());
            followFeedService.fanoutPublishedVideo(message);
        } catch (Exception exception) {
            int nextAttempt = retryCount == null ? 1 : retryCount + 1;
            if (nextAttempt >= MAX_ATTEMPTS) {
                deadLetterCounter.increment();
                log.warn("Follow-feed fanout exhausted broker-managed retries and will be dead-lettered, videoId={}, traceId={}, attempts={}",
                        message.getVideoId(), message.getTraceId(), nextAttempt);
                throw new AmqpRejectAndDontRequeueException("关注流扇出失败，已转入死信队列", exception);
            }
            rabbitTemplate.convertAndSend("", RabbitMQConstant.VIDEO_PUBLISH_INBOX_RETRY_QUEUE, message,
                    retryMessage -> {
                        retryMessage.getMessageProperties().setHeader(RETRY_COUNT_HEADER, nextAttempt);
                        return retryMessage;
                    });
            retryCounter.increment();
            log.warn("Follow-feed fanout failed and was moved to broker retry queue, videoId={}, traceId={}, nextAttempt={}",
                    message.getVideoId(), message.getTraceId(), nextAttempt);
        } finally {
            TraceContext.clear();
        }
    }
}
