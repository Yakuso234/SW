package com.jiake.jk.videoprocessor.consumer;

import com.jiake.jk.video.feign.VideoPrivateClient;
import com.jiake.jk.common.response.Result;
import com.jiake.jk.video.pojo.mq.VideoReviewMessage;
import com.jiake.jk.videoprocessor.service.VideoTranscodingService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class VideoConsumer {

    private final VideoPrivateClient videoPrivateClient;
    private final VideoTranscodingService videoTranscodingService;

    public VideoConsumer(VideoPrivateClient videoPrivateClient, VideoTranscodingService videoTranscodingService) {
        this.videoPrivateClient = videoPrivateClient;
        this.videoTranscodingService = videoTranscodingService;
    }

    @RabbitListener(queues = "video.review.queue")
    public void handleVideoPostMessage(VideoReviewMessage videoReviewMessage) {
        Result<Boolean> result = videoPrivateClient.claimVideoProcessing(videoReviewMessage.getVideoId());
        if (result.isError()) {
            // 抢占前任务仍处于 PENDING；直接死信会脱离处理租约恢复机制，因此保留消息重试。
            throw new IllegalStateException("视频处理任务状态迁移调用失败: " + result.getMsg());
        }
        if (Boolean.TRUE.equals(result.getData())) {
            log.info("Video {} claimed by processor and moved to PROCESSING", videoReviewMessage.getVideoId());
            try {
                VideoTranscodingService.TranscodingResult processed = videoTranscodingService
                        .transcode(videoReviewMessage.getVideoId(), videoReviewMessage.getVideoUrl());
                Result<Void> completeResult = videoPrivateClient.completeVideoProcessing(videoReviewMessage.getVideoId(),
                        new VideoPrivateClient.VideoProcessingResultRequest(processed.processedVideoKey(), processed.coverKey()));
                if (completeResult.isError()) {
                    throw new ResultCallbackUnavailableException("视频处理结果回写失败: " + completeResult.getMsg());
                }
            } catch (ResultCallbackUnavailableException exception) {
                // 结果已落对象存储，但状态回写不可用：保留 PROCESSING 租约，由恢复任务通过 Outbox 重新入队。
                throw new AmqpRejectAndDontRequeueException("处理结果回写暂不可用，等待租约恢复后重试", exception);
            } catch (Exception exception) {
                Result<Void> failResult = videoPrivateClient.failVideoProcessing(videoReviewMessage.getVideoId(),
                        new VideoPrivateClient.VideoProcessingFailureRequest(exception.getMessage()));
                if (failResult.isError()) {
                    throw new AmqpRejectAndDontRequeueException("视频失败结果回写失败，已转入死信队列: " + failResult.getMsg(), exception);
                }
                log.warn("Video {} processing failed and was marked REJECTED", videoReviewMessage.getVideoId(), exception);
            }
        } else {
            log.info("Video {} is already claimed or no longer processable, skip duplicate message", videoReviewMessage.getVideoId());
        }
    }

    private static class ResultCallbackUnavailableException extends RuntimeException {
        private ResultCallbackUnavailableException(String message) {
            super(message);
        }
    }
}
