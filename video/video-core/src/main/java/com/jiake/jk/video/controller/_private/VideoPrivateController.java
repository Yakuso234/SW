package com.jiake.jk.video.controller._private;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.video.service.VideoService;
import com.jiake.jk.video.service.VideoProcessingTaskService;
import com.jiake.jk.video.service.FollowFeedDeadLetterRecoveryService;
import com.jiake.jk.video.pojo.entity.Video;
import com.jiake.jk.video.pojo.response.VideoProcessingStatusResponse;
import com.jiake.jk.video.pojo.response.VideoRecoveryOperationResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "VideoPrivate")
@RestController
@RequestMapping("/private")
public class VideoPrivateController {

    @Autowired
    private VideoService videoService;
    @Autowired
    private VideoProcessingTaskService videoProcessingTaskService;
    @Autowired
    private FollowFeedDeadLetterRecoveryService followFeedDeadLetterRecoveryService;

    @PutMapping("/to-published/{videoId}")
    public Result<Void> putVideoStatusToPublished(@PathVariable Long videoId) {
        videoService.putVideoStatusToPublished(videoId);
        return Result.success();
    }

    @PutMapping("/status/{videoId}")
    public Result<Boolean> transitionVideoStatus(@PathVariable Long videoId,
                                                  @RequestParam("expectedStatus") Video.VideoStatus expectedStatus,
                                                  @RequestParam("targetStatus") Video.VideoStatus targetStatus) {
        return Result.success(videoService.transitionVideoStatus(videoId, expectedStatus, targetStatus));
    }

    @GetMapping("/creator/{creatorId}/processing/{videoId}")
    public Result<VideoProcessingStatusResponse> getCreatorVideoProcessingStatus(@PathVariable Long creatorId,
                                                                                   @PathVariable Long videoId) {
        return Result.success(videoService.getVideoProcessingStatus(creatorId, videoId));
    }

    @PutMapping("/processing/{videoId}/claim")
    public Result<Boolean> claimVideoProcessing(@PathVariable Long videoId) {
        return Result.success(videoProcessingTaskService.claimVideoProcessing(videoId));
    }

    @PutMapping("/processing/{videoId}/complete")
    public Result<Void> completeVideoProcessing(@PathVariable Long videoId,
                                                @RequestBody VideoProcessingResultRequest request) {
        videoProcessingTaskService.completeVideoProcessing(videoId, request.processedVideoKey(), request.coverKey());
        return Result.success();
    }

    @PutMapping("/processing/{videoId}/fail")
    public Result<Void> failVideoProcessing(@PathVariable Long videoId,
                                            @RequestBody VideoProcessingFailureRequest request) {
        videoProcessingTaskService.failVideoProcessing(videoId, request.errorMessage());
        return Result.success();
    }

    /**
     * 仅供内网运维入口读取。队列深度为 -1 表示 RabbitMQ 当前不可达或队列不存在。
     */
    @org.springframework.web.bind.annotation.GetMapping("/processing/operations/overview")
    public Result<VideoProcessingTaskService.ProcessingOperationsOverview> getProcessingOperationsOverview() {
        return Result.success(videoProcessingTaskService.getProcessingOperationsOverview());
    }

    /**
     * 仅允许恢复租约已过期且仍处于 PROCESSING 的任务；通过新 Outbox 投递，不直接重放 DLQ 原消息。
     */
    @org.springframework.web.bind.annotation.PostMapping("/processing/{videoId}/recover-expired")
    public Result<VideoRecoveryOperationResponse> recoverExpiredProcessingTask(
            @PathVariable Long videoId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-FlowPilot-Service", required = false) String requestedBy) {
        return Result.success(videoProcessingTaskService.recoverExpiredProcessingTask(
                videoId, idempotencyKey, traceId, requestedBy));
    }

    @GetMapping("/processing/{videoId}/recovery-status")
    public Result<VideoRecoveryOperationResponse> getRecoveryStatus(
            @PathVariable Long videoId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-FlowPilot-Service", required = false) String requestedBy) {
        return Result.success(videoProcessingTaskService.getRecoveryStatus(
                videoId, idempotencyKey, traceId, requestedBy));
    }

    /**
     * 仅供内网运维人工触发：先生成带唯一摘要的恢复审计和新 Outbox，再确认关注流死信。
     */
    @org.springframework.web.bind.annotation.PostMapping("/follow-feed/operations/recover-dead")
    public Result<FollowFeedDeadLetterRecoveryService.RecoveryResult> recoverFollowFeedDeadLetters(
            @RequestParam(defaultValue = "10") Integer batchSize) {
        return Result.success(followFeedDeadLetterRecoveryService.recover(batchSize));
    }

    public record VideoProcessingResultRequest(String processedVideoKey, String coverKey) { }

    public record VideoProcessingFailureRequest(String errorMessage) { }

}
