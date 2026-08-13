package com.jiake.jk.videoprocessor.consumer;

import com.jiake.jk.video.feign.VideoPrivateClient;
import com.jiake.jk.common.response.Result;
import com.jiake.jk.common.trace.TraceContext;
import com.jiake.jk.video.pojo.mq.VideoReviewMessage;
import com.jiake.jk.videoprocessor.service.VideoTranscodingService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class VideoConsumer {

    private final VideoPrivateClient videoPrivateClient;
    private final VideoTranscodingService videoTranscodingService;

    /**
     * 仅用于本地可控故障演练，默认关闭。开启后在转码完成、结果回写前暂停，
     * 便于稳定模拟 Video Service 回写不可用并验证处理租约恢复。
     */
    @Value("${sw.video-processing.fault-injection.enabled:false}")
    private boolean faultInjectionEnabled;

    @Value("${sw.video-processing.fault-injection.before-complete-callback-delay-ms:0}")
    private long beforeCompleteCallbackDelayMs;

    public VideoConsumer(VideoPrivateClient videoPrivateClient, VideoTranscodingService videoTranscodingService) {
        this.videoPrivateClient = videoPrivateClient;
        this.videoTranscodingService = videoTranscodingService;
    }

    @RabbitListener(queues = "video.review.queue")
    public void handleVideoPostMessage(VideoReviewMessage videoReviewMessage) {
        TraceContext.setTraceId(videoReviewMessage.getTraceId());
        try {
            handleMessageWithinTrace(videoReviewMessage);
        } finally {
            TraceContext.clear();
        }
    }

    private void handleMessageWithinTrace(VideoReviewMessage videoReviewMessage) {
        log.info("Received video processing message, videoId={}", videoReviewMessage.getVideoId());
        Result<Boolean> result = videoPrivateClient.claimVideoProcessing(videoReviewMessage.getVideoId());
        if (result.isError()) {
            // 抢占前任务仍处于 PENDING；直接死信会脱离处理租约恢复机制，因此保留消息重试。
            throw new IllegalStateException("视频处理任务状态迁移调用失败: " + result.getMsg());
        }
        if (Boolean.TRUE.equals(result.getData())) {
            log.info("Video {} claimed by processor and moved to PROCESSING", videoReviewMessage.getVideoId());
            VideoTranscodingService.TranscodingResult processed;
            try {
                processed = videoTranscodingService
                        .transcode(videoReviewMessage.getVideoId(), videoReviewMessage.getVideoUrl());
            } catch (Exception exception) {
                handleTranscodingFailure(videoReviewMessage.getVideoId(), exception);
                return;
            }

            try {
                delayBeforeCompletionCallbackIfConfigured(videoReviewMessage.getVideoId());
                Result<Void> completeResult = videoPrivateClient.completeVideoProcessing(videoReviewMessage.getVideoId(),
                        new VideoPrivateClient.VideoProcessingResultRequest(processed.processedVideoKey(), processed.coverKey()));
                if (completeResult.isError()) {
                    throw new ResultCallbackUnavailableException("视频处理结果回写失败: " + completeResult.getMsg());
                }
            } catch (Exception exception) {
                // 已抢占且结果已产出时，任何完成回写异常均保留租约，交给恢复任务补偿。
                throw new AmqpRejectAndDontRequeueException("处理结果回写暂不可用，等待租约恢复后重试", exception);
            }
        } else {
            log.info("Video {} is already claimed or no longer processable, skip duplicate message", videoReviewMessage.getVideoId());
        }
    }

    private void delayBeforeCompletionCallbackIfConfigured(Long videoId) {
        if (!faultInjectionEnabled || beforeCompleteCallbackDelayMs <= 0) {
            return;
        }

        log.warn("Local fault drill is delaying completion callback for video {} by {} ms", videoId,
                beforeCompleteCallbackDelayMs);
        try {
            Thread.sleep(beforeCompleteCallbackDelayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("本地故障演练等待被中断", exception);
        }
    }

    private static class ResultCallbackUnavailableException extends RuntimeException {
        private ResultCallbackUnavailableException(String message) {
            super(message);
        }
    }

    private void handleTranscodingFailure(Long videoId, Exception processingException) {
        try {
            Result<Void> failResult = videoPrivateClient.failVideoProcessing(videoId,
                    new VideoPrivateClient.VideoProcessingFailureRequest(processingException.getMessage()));
            if (failResult.isError()) {
                throw new ResultCallbackUnavailableException("视频失败结果回写失败: " + failResult.getMsg());
            }
            log.warn("Video {} processing failed and was marked REJECTED", videoId, processingException);
        } catch (Exception callbackException) {
            // 转码失败本身可拒绝，但失败状态回写不可用时不能丢弃任务，应由租约恢复补偿。
            throw new AmqpRejectAndDontRequeueException("视频失败结果回写暂不可用，等待租约恢复后重试", callbackException);
        }
    }
}
