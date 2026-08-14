package com.jiake.jk.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiake.jk.common.utils.SnowflakeUtils;
import com.jiake.jk.video.mapper.MessageOutBoxMapper;
import com.jiake.jk.video.mapper.VideoPublishInboxRecoveryMapper;
import com.jiake.jk.video.pojo.entity.MessageOutbox;
import com.jiake.jk.video.pojo.entity.VideoPublishInboxRecovery;
import com.jiake.jk.video.pojo.mq.VideoPublishedMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FollowFeedDeadLetterPreparationServiceTest {

    @Test
    void prepare_shouldCreateAuditAndPendingOutboxForFirstRecovery() throws Exception {
        MessageOutBoxMapper outboxMapper = mock(MessageOutBoxMapper.class);
        VideoPublishInboxRecoveryMapper recoveryMapper = mock(VideoPublishInboxRecoveryMapper.class);
        SnowflakeUtils snowflakeUtils = mock(SnowflakeUtils.class);
        when(snowflakeUtils.nextId()).thenReturn(8001L, 8002L);
        when(recoveryMapper.insertIgnore(any(VideoPublishInboxRecovery.class))).thenReturn(1);
        FollowFeedDeadLetterPreparationService service = new FollowFeedDeadLetterPreparationService(
                new ObjectMapper(), snowflakeUtils, outboxMapper, recoveryMapper);

        FollowFeedDeadLetterPreparationService.RecoveryPreparation result = service.prepare(messageBody());

        assertTrue(result.created());
        assertEquals(8001L, result.outboxId());
        ArgumentCaptor<MessageOutbox> outboxCaptor = ArgumentCaptor.forClass(MessageOutbox.class);
        verify(outboxMapper).insert(outboxCaptor.capture());
        assertEquals("video.publish.inbox.queue", outboxCaptor.getValue().getRoutingKey());
        assertEquals(MessageOutbox.OutboxStatus.PENDING, outboxCaptor.getValue().getStatus());
        assertEquals(messageBody(), outboxCaptor.getValue().getMessageBody());
    }

    @Test
    void prepare_shouldNotCreateSecondOutboxForKnownDeadLetter() {
        MessageOutBoxMapper outboxMapper = mock(MessageOutBoxMapper.class);
        VideoPublishInboxRecoveryMapper recoveryMapper = mock(VideoPublishInboxRecoveryMapper.class);
        SnowflakeUtils snowflakeUtils = mock(SnowflakeUtils.class);
        VideoPublishInboxRecovery existing = new VideoPublishInboxRecovery();
        existing.setOutboxId(9001L);
        existing.setRecoveryAttempt(1);
        MessageOutbox activeOutbox = new MessageOutbox();
        activeOutbox.setStatus(MessageOutbox.OutboxStatus.PENDING);
        when(recoveryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(outboxMapper.selectById(9001L)).thenReturn(activeOutbox);
        FollowFeedDeadLetterPreparationService service = new FollowFeedDeadLetterPreparationService(
                new ObjectMapper(), snowflakeUtils, outboxMapper, recoveryMapper);

        FollowFeedDeadLetterPreparationService.RecoveryPreparation result = service.prepare(messageBody());

        assertFalse(result.created());
        assertEquals(9001L, result.outboxId());
        verify(recoveryMapper, never()).insertIgnore(any());
        verify(outboxMapper, never()).insert(any());
    }

    @Test
    void prepare_shouldCreateNextAuditedAttemptAfterPriorOutboxCompleted() throws Exception {
        MessageOutBoxMapper outboxMapper = mock(MessageOutBoxMapper.class);
        VideoPublishInboxRecoveryMapper recoveryMapper = mock(VideoPublishInboxRecoveryMapper.class);
        SnowflakeUtils snowflakeUtils = mock(SnowflakeUtils.class);
        VideoPublishInboxRecovery existing = new VideoPublishInboxRecovery();
        existing.setOutboxId(9001L);
        existing.setRecoveryAttempt(1);
        MessageOutbox completedOutbox = new MessageOutbox();
        completedOutbox.setStatus(MessageOutbox.OutboxStatus.SUCCESS);
        when(recoveryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(outboxMapper.selectById(9001L)).thenReturn(completedOutbox);
        when(snowflakeUtils.nextId()).thenReturn(8001L, 8002L);
        when(recoveryMapper.insertIgnore(any(VideoPublishInboxRecovery.class))).thenReturn(1);
        FollowFeedDeadLetterPreparationService service = new FollowFeedDeadLetterPreparationService(
                new ObjectMapper(), snowflakeUtils, outboxMapper, recoveryMapper);

        FollowFeedDeadLetterPreparationService.RecoveryPreparation result = service.prepare(messageBody());

        assertTrue(result.created());
        ArgumentCaptor<VideoPublishInboxRecovery> recoveryCaptor = ArgumentCaptor.forClass(VideoPublishInboxRecovery.class);
        verify(recoveryMapper).insertIgnore(recoveryCaptor.capture());
        assertEquals(2, recoveryCaptor.getValue().getRecoveryAttempt());
    }

    private String messageBody() {
        return "{\"videoId\":501,\"creatorId\":601,\"publishedAt\":\"2026-08-14T12:00:00\",\"traceId\":\"dead-letter-test\"}";
    }
}
