package com.jiake.jk.videoprocessor.consumer;

import com.jiake.jk.video.feign.VideoPrivateClient;
import com.jiake.jk.video.pojo.mq.VideoReviewMessage;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class VideoConsumer {

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private VideoPrivateClient videoPrivateClient;

    @RabbitListener(queuesToDeclare = @Queue(name = "video.review.queue"))
    public void handleVideoPostMessage(VideoReviewMessage videoReviewMessage) {
        System.out.println(videoReviewMessage);
    }
}
