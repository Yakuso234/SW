package com.jiake.jk.video.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiake.jk.common.exception.YHClientException;
import com.jiake.jk.common.exception.YHServerException;
import com.jiake.jk.common.utils.SnowflakeUtils;
import com.jiake.jk.video.mapper.MessageOutBoxMapper;
import com.jiake.jk.video.mapper.VideoMapper;
import com.jiake.jk.video.mapper.VideoProcessingRecoveryRequestMapper;
import com.jiake.jk.video.mapper.VideoProcessingTaskMapper;
import com.jiake.jk.video.pojo.entity.MessageOutbox;
import com.jiake.jk.video.pojo.entity.Video;
import com.jiake.jk.video.pojo.entity.VideoProcessingRecoveryRequest;
import com.jiake.jk.video.pojo.entity.VideoProcessingTask;
import com.jiake.jk.video.service.impl.VideoProcessingTaskServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoRecoveryContractServiceTest {

    @Mock private VideoProcessingTaskMapper taskMapper;
    @Mock private VideoMapper videoMapper;
    @Mock private MessageOutBoxMapper outboxMapper;
    @Mock private VideoProcessingRecoveryRequestMapper recoveryMapper;
    @Mock private OutboxMessagePublisher publisher;
    @Mock private RabbitAdmin rabbitAdmin;
    @Mock private SnowflakeUtils snowflakeUtils;

    @BeforeAll
    static void initializeMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "VideoRecoveryContractServiceTest");
        TableInfoHelper.initTableInfo(assistant, Video.class);
        TableInfoHelper.initTableInfo(assistant, VideoProcessingTask.class);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sameKeyReplay_shouldReturnOriginalAcceptedReceiptWithoutAnotherOutbox() {
        VideoProcessingRecoveryRequest existing = receipt(
                2001L, 3001L, "key-1", "flowpilot",
                VideoProcessingRecoveryRequest.RecoveryStatus.ACCEPTED, 4001L);
        when(recoveryMapper.insertIgnore(any())).thenReturn(0);
        when(recoveryMapper.selectByIdempotencyKeyForUpdate("key-1")).thenReturn(existing);

        var response = service().recoverExpiredProcessingTask(3001L, "key-1", "trace-replay", "flowpilot");

        assertTrue(response.replayed());
        assertEquals("2001", response.recoveryId());
        assertEquals("4001", response.outboxId());
        assertEquals("trace-original", response.traceId());
        verify(taskMapper, never()).update(any(LambdaUpdateWrapper.class));
        verify(outboxMapper, never()).insert(any());
    }

    @Test
    void sameRejectedKeyReplay_shouldRemainRejectedWithoutOutbox() {
        VideoProcessingRecoveryRequest existing = receipt(
                2002L, 3002L, "key-rejected", "flowpilot",
                VideoProcessingRecoveryRequest.RecoveryStatus.REJECTED, null);
        existing.setReason(VideoProcessingRecoveryRequest.RecoveryReason.PRECONDITION_NOT_MET);
        when(recoveryMapper.insertIgnore(any())).thenReturn(0);
        when(recoveryMapper.selectByIdempotencyKeyForUpdate("key-rejected")).thenReturn(existing);

        var response = service().recoverExpiredProcessingTask(
                3002L, "key-rejected", "trace-new", "flowpilot");

        assertEquals("REJECTED", response.status());
        assertEquals("PRECONDITION_NOT_MET", response.reason());
        assertTrue(response.replayed());
        assertNull(response.outboxId());
        verify(outboxMapper, never()).insert(any());
    }

    @Test
    void preconditionFailure_shouldPersistRejectedReceiptWithoutOutbox() {
        when(snowflakeUtils.nextId()).thenReturn(2003L);
        when(recoveryMapper.insertIgnore(any())).thenReturn(1);
        when(taskMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(0);
        when(recoveryMapper.markPreconditionRejected(any(), any())).thenReturn(1);

        var response = service().recoverExpiredProcessingTask(
                3003L, "key-precondition", "trace-precondition", "flowpilot");

        assertEquals("REJECTED", response.status());
        assertEquals("PRECONDITION_NOT_MET", response.reason());
        assertNull(response.outboxId());
        verify(videoMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(outboxMapper, never()).insert(any());
    }

    @Test
    void sameKeyWithDifferentVideoOrService_shouldReturnConflict() {
        VideoProcessingRecoveryRequest existing = receipt(
                2004L, 3004L, "key-conflict", "flowpilot",
                VideoProcessingRecoveryRequest.RecoveryStatus.ACCEPTED, 4004L);
        when(recoveryMapper.insertIgnore(any())).thenReturn(0);
        when(recoveryMapper.selectByIdempotencyKeyForUpdate("key-conflict")).thenReturn(existing);

        ResponseStatusException videoConflict = assertThrows(ResponseStatusException.class,
                () -> service().recoverExpiredProcessingTask(
                        9999L, "key-conflict", "trace-conflict", "flowpilot"));
        assertEquals(HttpStatus.CONFLICT, videoConflict.getStatusCode());

        ResponseStatusException serviceConflict = assertThrows(ResponseStatusException.class,
                () -> service().recoverExpiredProcessingTask(
                        3004L, "key-conflict", "trace-conflict", "other-service"));
        assertEquals(HttpStatus.CONFLICT, serviceConflict.getStatusCode());
    }

    @Test
    void getRecoveryStatus_shouldBeReadOnlyAndReturnPersistedReceipt() {
        VideoProcessingRecoveryRequest existing = receipt(
                2005L, 3005L, "key-get", "flowpilot",
                VideoProcessingRecoveryRequest.RecoveryStatus.ACCEPTED, 4005L);
        when(recoveryMapper.selectByIdempotencyKey("key-get")).thenReturn(existing);

        var response = service().getRecoveryStatus(3005L, "key-get", "trace-get", "flowpilot");

        assertEquals("ACCEPTED", response.status());
        assertTrue(response.replayed());
        verify(recoveryMapper).selectByIdempotencyKey("key-get");
        verify(recoveryMapper, never()).insertIgnore(any());
        verify(taskMapper, never()).update(any(LambdaUpdateWrapper.class));
        verify(outboxMapper, never()).insert(any());
    }

    @Test
    void getRecoveryStatus_shouldReturnNotFoundOrConflict() {
        when(recoveryMapper.selectByIdempotencyKey("missing-key")).thenReturn(null);
        ResponseStatusException notFound = assertThrows(ResponseStatusException.class,
                () -> service().getRecoveryStatus(3006L, "missing-key", "trace-get", "flowpilot"));
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());

        VideoProcessingRecoveryRequest existing = receipt(
                2006L, 3006L, "conflict-key", "flowpilot",
                VideoProcessingRecoveryRequest.RecoveryStatus.REJECTED, null);
        when(recoveryMapper.selectByIdempotencyKey("conflict-key")).thenReturn(existing);
        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
                () -> service().getRecoveryStatus(3007L, "conflict-key", "trace-get", "flowpilot"));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
    }

    @Test
    void acceptedReceiptWriteFailure_shouldPropagateForTransactionRollback() {
        Video video = new Video();
        video.setId(3008L);
        video.setUrl("recovery/3008.mp4");
        when(snowflakeUtils.nextId()).thenReturn(2008L, 4008L);
        when(recoveryMapper.insertIgnore(any())).thenReturn(1);
        when(taskMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(videoMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(videoMapper.selectById(3008L)).thenReturn(video);
        when(outboxMapper.insert(any(MessageOutbox.class))).thenReturn(1);
        when(recoveryMapper.markAccepted(any(), any(), any())).thenReturn(0);
        TransactionSynchronizationManager.initSynchronization();

        assertThrows(YHServerException.class, () -> service().recoverExpiredProcessingTask(
                3008L, "key-rollback", "trace-rollback", "flowpilot"));
    }

    @Test
    void invalidHeaders_shouldFailBeforeClaimingKey() {
        assertThrows(YHClientException.class, () -> service().recoverExpiredProcessingTask(
                3009L, " ", "trace", "flowpilot"));
        assertThrows(YHClientException.class, () -> service().getRecoveryStatus(
                3009L, "key", "trace", " "));
        verify(recoveryMapper, never()).insertIgnore(any());
    }

    private VideoProcessingTaskServiceImpl service() {
        return new VideoProcessingTaskServiceImpl(taskMapper, videoMapper, outboxMapper, recoveryMapper,
                new ObjectMapper().findAndRegisterModules(), publisher, rabbitAdmin, snowflakeUtils);
    }

    private VideoProcessingRecoveryRequest receipt(Long id, Long videoId, String key, String requestedBy,
                                                     VideoProcessingRecoveryRequest.RecoveryStatus status,
                                                     Long outboxId) {
        VideoProcessingRecoveryRequest receipt = new VideoProcessingRecoveryRequest();
        receipt.setId(id);
        receipt.setVideoId(videoId);
        receipt.setIdempotencyKey(key);
        receipt.setRequestedBy(requestedBy);
        receipt.setTraceId("trace-original");
        receipt.setStatus(status);
        receipt.setOutboxId(outboxId);
        receipt.setCreatedAt(LocalDateTime.of(2026, 8, 24, 10, 0));
        receipt.setUpdatedAt(receipt.getCreatedAt());
        return receipt;
    }
}
