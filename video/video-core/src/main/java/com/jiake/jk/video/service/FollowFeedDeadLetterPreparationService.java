package com.jiake.jk.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiake.jk.common.exception.YHClientException;
import com.jiake.jk.common.utils.SnowflakeUtils;
import com.jiake.jk.video.constant.RabbitMQConstant;
import com.jiake.jk.video.mapper.MessageOutBoxMapper;
import com.jiake.jk.video.mapper.VideoPublishInboxRecoveryMapper;
import com.jiake.jk.video.pojo.entity.MessageOutbox;
import com.jiake.jk.video.pojo.entity.VideoPublishInboxRecovery;
import com.jiake.jk.video.pojo.mq.VideoPublishedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Persists a replay intent before an operator acknowledges a dead-letter message. */
@Service
@RequiredArgsConstructor
public class FollowFeedDeadLetterPreparationService {

    private final ObjectMapper objectMapper;
    private final SnowflakeUtils snowflakeUtils;
    private final MessageOutBoxMapper messageOutBoxMapper;
    private final VideoPublishInboxRecoveryMapper recoveryMapper;

    @Transactional
    public RecoveryPreparation prepare(String messageBody) {
        VideoPublishedMessage message = parseAndValidate(messageBody);
        String digest = sha256(messageBody);
        VideoPublishInboxRecovery existing = findLatestRecovery(digest);
        int recoveryAttempt = 1;
        if (existing != null) {
            MessageOutbox existingOutbox = messageOutBoxMapper.selectById(existing.getOutboxId());
            if (existingOutbox == null) {
                throw new IllegalStateException("关注流死信恢复审计缺少对应 Outbox");
            }
            if (existingOutbox.getStatus() != MessageOutbox.OutboxStatus.SUCCESS
                    && existingOutbox.getStatus() != MessageOutbox.OutboxStatus.DEAD) {
                return new RecoveryPreparation(existing.getOutboxId(), false, message.getVideoId(), message.getTraceId());
            }
            recoveryAttempt = (existing.getRecoveryAttempt() == null ? 1 : existing.getRecoveryAttempt()) + 1;
        }

        long outboxId = snowflakeUtils.nextId();
        VideoPublishInboxRecovery recovery = new VideoPublishInboxRecovery();
        recovery.setId(snowflakeUtils.nextId());
        recovery.setMessageDigest(digest);
        recovery.setVideoId(message.getVideoId());
        recovery.setCreatorId(message.getCreatorId());
        recovery.setTraceId(message.getTraceId());
        recovery.setRecoveryAttempt(recoveryAttempt);
        recovery.setOutboxId(outboxId);
        if (recoveryMapper.insertIgnore(recovery) == 0) {
            VideoPublishInboxRecovery concurrent = findLatestRecovery(digest);
            if (concurrent == null) {
                throw new IllegalStateException("关注流死信恢复审计记录创建失败");
            }
            return new RecoveryPreparation(concurrent.getOutboxId(), false, message.getVideoId(), message.getTraceId());
        }

        MessageOutbox outbox = new MessageOutbox();
        outbox.setId(outboxId);
        outbox.setBusinessId(message.getVideoId());
        outbox.setExchangeName("");
        outbox.setRoutingKey(RabbitMQConstant.VIDEO_PUBLISH_INBOX_QUEUE);
        outbox.setMessageBody(messageBody);
        outbox.setStatus(MessageOutbox.OutboxStatus.PENDING);
        outbox.setRetryCount(0);
        messageOutBoxMapper.insert(outbox);
        return new RecoveryPreparation(outboxId, true, message.getVideoId(), message.getTraceId());
    }

    private VideoPublishInboxRecovery findLatestRecovery(String digest) {
        return recoveryMapper.selectOne(new LambdaQueryWrapper<VideoPublishInboxRecovery>()
                .eq(VideoPublishInboxRecovery::getMessageDigest, digest)
                .orderByDesc(VideoPublishInboxRecovery::getRecoveryAttempt)
                .last("LIMIT 1"));
    }

    private VideoPublishedMessage parseAndValidate(String messageBody) {
        try {
            VideoPublishedMessage message = objectMapper.readValue(messageBody, VideoPublishedMessage.class);
            if (message.getVideoId() == null || message.getCreatorId() == null
                    || message.getPublishedAt() == null || message.getPublishedAt().isBlank()) {
                throw new YHClientException("关注流死信事件字段不完整，拒绝恢复");
            }
            return message;
        } catch (JsonProcessingException exception) {
            throw new YHClientException("关注流死信事件格式无效，拒绝恢复");
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) builder.append(String.format("%02x", item));
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 算法不可用", exception);
        }
    }

    public record RecoveryPreparation(Long outboxId, boolean created, Long videoId, String traceId) { }
}
