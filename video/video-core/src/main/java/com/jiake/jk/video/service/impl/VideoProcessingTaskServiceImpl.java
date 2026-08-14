package com.jiake.jk.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiake.jk.common.exception.YHServerException;
import com.jiake.jk.common.trace.TraceContext;
import com.jiake.jk.video.constant.RabbitMQConstant;
import com.jiake.jk.video.mapper.MessageOutBoxMapper;
import com.jiake.jk.video.mapper.VideoMapper;
import com.jiake.jk.video.mapper.VideoProcessingTaskMapper;
import com.jiake.jk.video.pojo.entity.MessageOutbox;
import com.jiake.jk.video.pojo.entity.Video;
import com.jiake.jk.video.pojo.entity.VideoProcessingTask;
import com.jiake.jk.video.pojo.mq.VideoReviewMessage;
import com.jiake.jk.video.service.OutboxMessagePublisher;
import com.jiake.jk.video.service.VideoProcessingTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

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

    private final VideoProcessingTaskMapper videoProcessingTaskMapper;
    private final VideoMapper videoMapper;
    private final MessageOutBoxMapper messageOutBoxMapper;
    private final ObjectMapper objectMapper;
    private final OutboxMessagePublisher outboxMessagePublisher;
    private final RabbitAdmin rabbitAdmin;

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
    public boolean recoverExpiredProcessingTask(Long videoId) {
        VideoProcessingTask task = videoProcessingTaskMapper.selectOne(
                new LambdaQueryWrapper<VideoProcessingTask>()
                        .select(VideoProcessingTask::getId, VideoProcessingTask::getVideoId)
                        .eq(VideoProcessingTask::getVideoId, videoId)
                        .eq(VideoProcessingTask::getStatus, VideoProcessingTask.ProcessingStatus.PROCESSING)
                        .le(VideoProcessingTask::getLeaseExpireAt, LocalDateTime.now()));
        return task != null && recoverExpiredTask(task, LocalDateTime.now());
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
        createRecoveryOutbox(task.getVideoId());
        return true;
    }

    private long getQueueMessageCount(String queueName) {
        QueueInformation queueInformation = rabbitAdmin.getQueueInfo(queueName);
        return queueInformation == null ? -1 : queueInformation.getMessageCount();
    }

    private void createRecoveryOutbox(Long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null) {
            log.warn("Skip recovery enqueue because video {} no longer exists", videoId);
            return;
        }
        VideoReviewMessage message = new VideoReviewMessage();
        message.setTraceId(TraceContext.getOrCreateTraceId());
        message.setVideoId(videoId);
        message.setVideoUrl(video.getUrl());
        message.setDescription(video.getDescription());
        try {
            MessageOutbox outbox = new MessageOutbox();
            outbox.setBusinessId(videoId);
            outbox.setExchangeName("");
            outbox.setRoutingKey(RabbitMQConstant.VIDEO_REVIEW_QUEUE);
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
            throw new YHServerException("序列化视频恢复消息失败");
        }
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
