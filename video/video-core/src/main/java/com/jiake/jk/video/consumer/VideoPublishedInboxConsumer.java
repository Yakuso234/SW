package com.jiake.jk.video.consumer;

import com.jiake.jk.common.trace.TraceContext;
import com.jiake.jk.video.constant.RabbitMQConstant;
import com.jiake.jk.video.pojo.mq.VideoPublishedMessage;
import com.jiake.jk.video.service.FollowFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoPublishedInboxConsumer {

    private final FollowFeedService followFeedService;

    @RabbitListener(queues = RabbitMQConstant.VIDEO_PUBLISH_INBOX_QUEUE)
    public void consume(VideoPublishedMessage message) {
        try {
            TraceContext.setTraceId(message.getTraceId());
            followFeedService.fanoutPublishedVideo(message);
        } finally {
            TraceContext.clear();
        }
    }
}
