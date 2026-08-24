package com.jiake.jk.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiake.jk.common.exception.YHClientException;
import com.jiake.jk.common.exception.YHServerException;
import com.jiake.jk.common.trace.TraceContext;
import com.jiake.jk.common.utils.SnowflakeUtils;
import com.jiake.jk.video.constant.RabbitMQConstant;
import com.jiake.jk.video.mapper.MessageOutBoxMapper;
import com.jiake.jk.video.mapper.VideoMapper;
import com.jiake.jk.video.mapper.VideoProcessingRecoveryRequestMapper;
import com.jiake.jk.video.mapper.VideoProcessingTaskMapper;
import com.jiake.jk.video.pojo.entity.MessageOutbox;
import com.jiake.jk.video.pojo.entity.Video;
import com.jiake.jk.video.pojo.entity.VideoProcessingRecoveryRequest;
import com.jiake.jk.video.pojo.entity.VideoProcessingTask;
import com.jiake.jk.video.pojo.mq.VideoReviewMessage;
import com.jiake.jk.video.pojo.response.VideoRecoveryOperationResponse;
import com.jiake.jk.video.service.OutboxMessagePublisher;
import com.jiake.jk.video.service.VideoProcessingTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoProcessingTaskServiceImpl implements VideoProcessingTaskService {

    private static final int RECOVERY_BATCH_SIZE = 100;
    private static final long DEFAULT_LEASE_SECONDS = 600;

    /**
     * 生产默认保留 10 分钟租约；仅本地故障演练可用运行参数缩短等待时间。
     */
    @Value("${sw.video-processing.lease-seconds:600}")
    private long leaseSeconds = DEFAULT_LEASE_SECONDS;

    /**
     * 自动扫描默认开启。仅本地故障演练可暂停扫描，以便由人工或外部恢复编排器稳定地触发一次恢复。
     * 手工恢复接口不受此开关影响。
     */
    @Value("${sw.video-processing.automatic-recovery-enabled:true}")
    private boolean automaticRecoveryEnabled = true;

    private final VideoProcessingTaskMapper videoProcessingTaskMapper;
    private final VideoMapper videoMapper;
    private final MessageOutBoxMapper messageOutBoxMapper;
    private final VideoProcessingRecoveryRequestMapper recoveryRequestMapper;
    private final ObjectMapper objectMapper;
    private final OutboxMessagePublisher outboxMessagePublisher;
    private final RabbitAdmin rabbitAdmin;
    private final SnowflakeUtils snowflakeUtils;

    @Override
    @Transactional
    public boolean claimVideoProcessing(Long videoId) {
        int videoClaimed = videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .set(Video::getStatus, Video.VideoStatus.PROCESSING)
                .eq(Video::getId, videoId)
                .eq(Video::getStatus, Video.VideoStatus.PENDING_REVIEW));
        if (videoClaimed == 0) {
            return false;
        }

        int taskClaimed = videoProcessingTaskMapper.update(new LambdaUpdateWrapper<VideoProcessingTask>()
                .set(VideoProcessingTask::getStatus, VideoProcessingTask.ProcessingStatus.PROCESSING)
                .set(VideoProcessingTask::getLeaseExpireAt, LocalDateTime.now().plusSeconds(Math.max(1, leaseSeconds)))
                .setSql("retry_count = retry_count + 1")
                .eq(VideoProcessingTask::getVideoId, videoId)
                .eq(VideoProcessingTask::getStatus, VideoProcessingTask.ProcessingStatus.PENDING));
        if (taskClaimed != 1) {
            throw new YHServerException("视频处理任务不存在或状态异常");
        }
        return true;
    }

    @Override
    @Transactional
    public void completeVideoProcessing(Long videoId, String processedVideoKey, String coverKey) {
        int taskCompleted = videoProcessingTaskMapper.update(new LambdaUpdateWrapper<VideoProcessingTask>()
                .set(VideoProcessingTask::getStatus, VideoProcessingTask.ProcessingStatus.SUCCEEDED)
                .set(VideoProcessingTask::getLeaseExpireAt, null)
                .set(VideoProcessingTask::getErrorMessage, null)
                .eq(VideoProcessingTask::getVideoId, videoId)
                .eq(VideoProcessingTask::getStatus, VideoProcessingTask.ProcessingStatus.PROCESSING));
        if (taskCompleted != 1) {
            throw new YHServerException("视频处理任务已不处于处理中");
        }
        int videoPublished = videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .set(Video::getUrl, processedVideoKey)
                .set(Video::getCoverUrl, coverKey)
                .set(Video::getStatus, Video.VideoStatus.PUBLISHED)
                .set(Video::getPublishedAt, LocalDateTime.now())
                .eq(Video::getId, videoId)
                .eq(Video::getStatus, Video.VideoStatus.PROCESSING));
        if (videoPublished != 1) {
            throw new YHServerException("视频状态已变化，拒绝写入处理结果");
        }
        createPublishedInboxOutbox(videoId);
    }

    @Override
    @Transactional
    public void failVideoProcessing(Long videoId, String errorMessage) {
        String safeMessage = errorMessage == null ? "未知处理失败" : errorMessage.substring(0, Math.min(errorMessage.length(), 512));
        int taskFailed = videoProcessingTaskMapper.update(new LambdaUpdateWrapper<VideoProcessingTask>()
                .set(VideoProcessingTask::getStatus, VideoProcessingTask.ProcessingStatus.FAILED)
                .set(VideoProcessingTask::getLeaseExpireAt, null)
                .set(VideoProcessingTask::getErrorMessage, safeMessage)
                .eq(VideoProcessingTask::getVideoId, videoId)
                .eq(VideoProcessingTask::getStatus, VideoProcessingTask.ProcessingStatus.PROCESSING));
        if (taskFailed != 1) {
            throw new YHServerException("视频处理任务已不处于处理中");
        }
        int videoRejected = videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .set(Video::getStatus, Video.VideoStatus.REJECTED)
                .eq(Video::getId, videoId)
                .eq(Video::getStatus, Video.VideoStatus.PROCESSING));
        if (videoRejected != 1) {
            throw new YHServerException("视频状态已变化，拒绝写入失败结果");
        }
    }

    @Override
    @Transactional
    @Scheduled(fixedDelayString = "${sw.video-processing.recovery-delay-ms:60000}")
    public int recoverExpiredProcessingTasks() {
        if (!automaticRecoveryEnabled) {
            log.debug("Automatic expired video-processing recovery is disabled for this runtime");
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        List<VideoProcessingTask> expiredTasks = videoProcessingTaskMapper.selectList(
                new LambdaQueryWrapper<VideoProcessingTask>()
                        .select(VideoProcessingTask::getId, VideoProcessingTask::getVideoId)
                        .eq(VideoProcessingTask::getStatus, VideoProcessingTask.ProcessingStatus.PROCESSING)
                        .le(VideoProcessingTask::getLeaseExpireAt, now)
                        .orderByAsc(VideoProcessingTask::getLeaseExpireAt)
                        .last("LIMIT " + RECOVERY_BATCH_SIZE));
        int recovered = 0;
        for (VideoProcessingTask task : expiredTasks) {
            if (recoverExpiredTask(task, now)) {
                recovered++;
            }
        }
        return recovered;
    }

    @Override
    @Transactional
    public VideoRecoveryOperationResponse recoverExpiredProcessingTask(Long videoId, String idempotencyKey,
                                                                        String traceId, String requestedBy) {
        RecoveryHeaders headers = validateRecoveryHeaders(videoId, idempotencyKey, traceId, requestedBy);
        LocalDateTime now = LocalDateTime.now();
        VideoProcessingRecoveryRequest receipt = new VideoProcessingRecoveryRequest();
        receipt.setId(snowflakeUtils.nextId());
        receipt.setIdempotencyKey(headers.idempotencyKey());
        receipt.setVideoId(videoId);
        receipt.setRequestedBy(headers.requestedBy());
        receipt.setTraceId(headers.traceId());
        receipt.setStatus(VideoProcessingRecoveryRequest.RecoveryStatus.PENDING);
        receipt.setCreatedAt(now);
        receipt.setUpdatedAt(now);

        int claimed = recoveryRequestMapper.insertIgnore(receipt);
        if (claimed == 0) {
            VideoProcessingRecoveryRequest existing = recoveryRequestMapper
                    .selectByIdempotencyKeyForUpdate(headers.idempotencyKey());
            if (existing == null) {
                throw new YHServerException("恢复幂等键竞争后无法读取持久化回执");
            }
            validateReceiptOwner(existing, videoId, headers.requestedBy());
            if (existing.getStatus() == VideoProcessingRecoveryRequest.RecoveryStatus.PENDING) {
                throw new YHServerException("恢复请求仍处于未完成状态，请稍后对账");
            }
            return toRecoveryResponse(existing, true);
        }
        if (claimed != 1) {
            throw new YHServerException("恢复幂等键抢占结果异常");
        }

        int taskReset = videoProcessingTaskMapper.update(new LambdaUpdateWrapper<VideoProcessingTask>()
                .set(VideoProcessingTask::getStatus, VideoProcessingTask.ProcessingStatus.PENDING)
                .set(VideoProcessingTask::getLeaseExpireAt, null)
                .eq(VideoProcessingTask::getVideoId, videoId)
                .eq(VideoProcessingTask::getStatus, VideoProcessingTask.ProcessingStatus.PROCESSING)
                .le(VideoProcessingTask::getLeaseExpireAt, now));
        if (taskReset == 0) {
            if (recoveryRequestMapper.markPreconditionRejected(receipt.getId(), now) != 1) {
                throw new YHServerException("恢复拒绝回执写入失败");
            }
            receipt.setStatus(VideoProcessingRecoveryRequest.RecoveryStatus.REJECTED);
            receipt.setReason(VideoProcessingRecoveryRequest.RecoveryReason.PRECONDITION_NOT_MET);
            return toRecoveryResponse(receipt, false);
        }

        int videoRestored = videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .set(Video::getStatus, Video.VideoStatus.PENDING_REVIEW)
                .eq(Video::getId, videoId)
                .eq(Video::getStatus, Video.VideoStatus.PROCESSING));
        if (videoRestored != 1) {
            throw new YHServerException("视频处理任务与视频状态不一致");
        }
        Long outboxId = createRecoveryOutbox(videoId, headers.traceId());
        if (recoveryRequestMapper.markAccepted(receipt.getId(), outboxId, now) != 1) {
            throw new YHServerException("恢复接受回执写入失败");
        }
        receipt.setStatus(VideoProcessingRecoveryRequest.RecoveryStatus.ACCEPTED);
        receipt.setOutboxId(outboxId);
        return toRecoveryResponse(receipt, false);
    }

    @Override
    @Transactional(readOnly = true)
    public VideoRecoveryOperationResponse getRecoveryStatus(Long videoId, String idempotencyKey,
                                                             String traceId, String requestedBy) {
        RecoveryHeaders headers = validateRecoveryHeaders(videoId, idempotencyKey, traceId, requestedBy);
        VideoProcessingRecoveryRequest receipt = recoveryRequestMapper
                .selectByIdempotencyKey(headers.idempotencyKey());
        if (receipt == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "恢复回执不存在");
        }
        validateReceiptOwner(receipt, videoId, headers.requestedBy());
        if (receipt.getStatus() == VideoProcessingRecoveryRequest.RecoveryStatus.PENDING) {
            throw new YHServerException("恢复请求仍处于未完成状态，请稍后对账");
        }
        return toRecoveryResponse(receipt, true);
    }

    @Override
    public ProcessingOperationsOverview getProcessingOperationsOverview() {
        return new ProcessingOperationsOverview(
                getQueueMessageCount(RabbitMQConstant.VIDEO_REVIEW_QUEUE),
                getQueueMessageCount(RabbitMQConstant.VIDEO_REVIEW_DEAD_QUEUE),
                videoProcessingTaskMapper.selectCount(new LambdaQueryWrapper<VideoProcessingTask>()
                        .eq(VideoProcessingTask::getStatus, VideoProcessingTask.ProcessingStatus.PROCESSING)),
                videoProcessingTaskMapper.selectCount(new LambdaQueryWrapper<VideoProcessingTask>()
                        .eq(VideoProcessingTask::getStatus, VideoProcessingTask.ProcessingStatus.FAILED)));
    }

    private boolean recoverExpiredTask(VideoProcessingTask task, LocalDateTime now) {
        int reset = videoProcessingTaskMapper.update(new LambdaUpdateWrapper<VideoProcessingTask>()
                .set(VideoProcessingTask::getStatus, VideoProcessingTask.ProcessingStatus.PENDING)
                .set(VideoProcessingTask::getLeaseExpireAt, null)
                .eq(VideoProcessingTask::getId, task.getId())
                .eq(VideoProcessingTask::getStatus, VideoProcessingTask.ProcessingStatus.PROCESSING)
                .le(VideoProcessingTask::getLeaseExpireAt, now));
        if (reset == 0) {
            return false;
        }
        int videoRestored = videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .set(Video::getStatus, Video.VideoStatus.PENDING_REVIEW)
                .eq(Video::getId, task.getVideoId())
                .eq(Video::getStatus, Video.VideoStatus.PROCESSING));
        if (videoRestored != 1) {
            throw new YHServerException("视频处理任务与视频状态不一致");
        }
        createRecoveryOutbox(task.getVideoId(), TraceContext.getOrCreateTraceId());
        return true;
    }

    private long getQueueMessageCount(String queueName) {
        QueueInformation queueInformation = rabbitAdmin.getQueueInfo(queueName);
        return queueInformation == null ? -1 : queueInformation.getMessageCount();
    }

    private Long createRecoveryOutbox(Long videoId, String traceId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            throw new YHServerException("恢复视频不存在，拒绝创建 Outbox");
        }
        VideoReviewMessage message = new VideoReviewMessage();
        message.setTraceId(traceId);
        message.setVideoId(videoId);
        message.setVideoUrl(video.getUrl());
        message.setDescription(video.getDescription());
        try {
            MessageOutbox outbox = new MessageOutbox();
            outbox.setId(snowflakeUtils.nextId());
            outbox.setBusinessId(videoId);
            outbox.setExchangeName("");
            outbox.setRoutingKey(RabbitMQConstant.VIDEO_REVIEW_QUEUE);
            outbox.setMessageBody(objectMapper.writeValueAsString(message));
            outbox.setStatus(MessageOutbox.OutboxStatus.PENDING);
            outbox.setRetryCount(0);
            if (messageOutBoxMapper.insert(outbox) != 1) {
                throw new YHServerException("视频恢复 Outbox 写入失败");
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    outboxMessagePublisher.publish(outbox.getId());
                }
            });
            return outbox.getId();
        } catch (JsonProcessingException exception) {
            throw new YHServerException("序列化视频恢复消息失败");
        }
    }

    private RecoveryHeaders validateRecoveryHeaders(Long videoId, String idempotencyKey,
                                                     String traceId, String requestedBy) {
        if (videoId == null || videoId <= 0) {
            throw new YHClientException("videoId 非法");
        }
        return new RecoveryHeaders(
                requireHeader(idempotencyKey, 255, "Idempotency-Key"),
                requireHeader(traceId, 128, "X-Trace-Id"),
                requireHeader(requestedBy, 64, "X-FlowPilot-Service"));
    }

    private String requireHeader(String value, int maxLength, String name) {
        if (value == null || value.isBlank()) {
            throw new YHClientException(name + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new YHClientException(name + " 非法或超过长度限制");
        }
        return normalized;
    }

    private void validateReceiptOwner(VideoProcessingRecoveryRequest receipt, Long videoId, String requestedBy) {
        if (!Objects.equals(receipt.getVideoId(), videoId)
                || !Objects.equals(receipt.getRequestedBy(), requestedBy)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "幂等键已绑定到其他视频或服务");
        }
    }

    private VideoRecoveryOperationResponse toRecoveryResponse(VideoProcessingRecoveryRequest receipt,
                                                                boolean replayed) {
        return new VideoRecoveryOperationResponse(
                String.valueOf(receipt.getId()),
                receipt.getVideoId(),
                receipt.getIdempotencyKey(),
                receipt.getStatus().getValue(),
                receipt.getReason() == null ? null : receipt.getReason().getValue(),
                receipt.getOutboxId() == null ? null : String.valueOf(receipt.getOutboxId()),
                receipt.getTraceId(),
                receipt.getRequestedBy(),
                replayed,
                receipt.getCreatedAt());
    }

    private record RecoveryHeaders(String idempotencyKey, String traceId, String requestedBy) {
    }

    private void createPublishedInboxOutbox(Long videoId) {
        try {
            Video video = videoMapper.selectById(videoId);
            if (video == null || video.getPublishedAt() == null) {
                throw new YHServerException("已发布视频不存在或缺少发布时间");
            }
            com.jiake.jk.video.pojo.mq.VideoPublishedMessage message =
                    new com.jiake.jk.video.pojo.mq.VideoPublishedMessage();
            message.setVideoId(videoId);
            message.setCreatorId(video.getCreatorId());
            message.setPublishedAt(video.getPublishedAt().toString());
            message.setTraceId(TraceContext.getOrCreateTraceId());

            MessageOutbox outbox = new MessageOutbox();
            outbox.setBusinessId(videoId);
            outbox.setExchangeName("");
            outbox.setRoutingKey(RabbitMQConstant.VIDEO_PUBLISH_INBOX_QUEUE);
            outbox.setMessageBody(objectMapper.writeValueAsString(message));
            outbox.setStatus(MessageOutbox.OutboxStatus.PENDING);
            outbox.setRetryCount(0);
            messageOutBoxMapper.insert(outbox);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    outboxMessagePublisher.publish(outbox.getId());
                }
            });
        } catch (JsonProcessingException exception) {
            throw new YHServerException("发布关注流事件序列化失败");
        }
    }
}
