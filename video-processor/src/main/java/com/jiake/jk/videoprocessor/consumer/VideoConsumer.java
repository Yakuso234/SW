package com.jiake.jk.videoprocessor.consumer;

import com.jiake.jk.video.feign.VideoPrivateClient;
import com.jiake.jk.common.response.Result;
import com.jiake.jk.video.pojo.entity.Video;
import com.jiake.jk.video.pojo.mq.VideoReviewMessage;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class VideoConsumer {

    private final VideoPrivateClient videoPrivateClient;

    public VideoConsumer(VideoPrivateClient videoPrivateClient) {
        this.videoPrivateClient = videoPrivateClient;
    }

    @RabbitListener(queuesToDeclare = @Queue(name = "video.review.queue"))
    public void handleVideoPostMessage(VideoReviewMessage videoReviewMessage) {
        Result<Boolean> result = videoPrivateClient.transitionVideoStatus(
                videoReviewMessage.getVideoId(), Video.VideoStatus.PENDING_REVIEW, Video.VideoStatus.PROCESSING);
        if (result.isError()) {
            throw new IllegalStateException("视频处理任务状态迁移调用失败: " + result.getMsg());
        }
        if (Boolean.TRUE.equals(result.getData())) {
            log.info("Video {} claimed by processor and moved to PROCESSING", videoReviewMessage.getVideoId());
            // 下一步在此执行 FFmpeg 转码/抽帧，并根据结果调用 PROCESSING -> PUBLISHED/REJECTED。
        } else {
            log.info("Video {} is already claimed or no longer processable, skip duplicate message", videoReviewMessage.getVideoId());
        }
    }
}
