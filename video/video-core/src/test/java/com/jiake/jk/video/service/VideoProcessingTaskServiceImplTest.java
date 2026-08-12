package com.jiake.jk.video.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiake.jk.video.mapper.MessageOutBoxMapper;
import com.jiake.jk.video.mapper.VideoMapper;
import com.jiake.jk.video.mapper.VideoProcessingTaskMapper;
import com.jiake.jk.video.pojo.entity.MessageOutbox;
import com.jiake.jk.video.pojo.entity.Video;
import com.jiake.jk.video.pojo.entity.VideoProcessingTask;
import com.jiake.jk.video.service.impl.VideoProcessingTaskServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoProcessingTaskServiceImplTest {

    @Mock private VideoProcessingTaskMapper videoProcessingTaskMapper;
    @Mock private VideoMapper videoMapper;
    @Mock private MessageOutBoxMapper messageOutBoxMapper;
    @Mock private OutboxMessagePublisher outboxMessagePublisher;
    @Mock private RabbitAdmin rabbitAdmin;

    @BeforeAll
    static void initializeMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "VideoProcessingTaskServiceImplTest");
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
    void claimVideoProcessing_shouldClaimVideoAndTaskWithLease() {
        VideoProcessingTaskServiceImpl service = new VideoProcessingTaskServiceImpl(
                videoProcessingTaskMapper, videoMapper, messageOutBoxMapper, new ObjectMapper(), outboxMessagePublisher, rabbitAdmin);
        when(videoMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(videoProcessingTaskMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);

        assertTrue(service.claimVideoProcessing(801L));

        ArgumentCaptor<LambdaUpdateWrapper<Video>> videoCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(videoMapper).update(isNull(), videoCaptor.capture());
        assertTrue(videoCaptor.getValue().getParamNameValuePairs().containsValue(Video.VideoStatus.PROCESSING));
        ArgumentCaptor<LambdaUpdateWrapper<VideoProcessingTask>> taskCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(videoProcessingTaskMapper).update(taskCaptor.capture());
        assertTrue(taskCaptor.getValue().getParamNameValuePairs()
                .containsValue(VideoProcessingTask.ProcessingStatus.PROCESSING));
        assertTrue(taskCaptor.getValue().getSqlSet().contains("retry_count"));
    }

    @Test
    void recoverExpiredProcessingTasks_shouldResetTaskAndCreateRecoveryOutbox() {
        VideoProcessingTaskServiceImpl service = new VideoProcessingTaskServiceImpl(
                videoProcessingTaskMapper, videoMapper, messageOutBoxMapper, new ObjectMapper(), outboxMessagePublisher, rabbitAdmin);
        VideoProcessingTask expiredTask = new VideoProcessingTask();
        expiredTask.setId(802L);
        expiredTask.setVideoId(902L);
        Video video = new Video();
        video.setId(902L);
        video.setUrl("2026/08/recovery.mp4");
        video.setDescription("租约恢复测试");
        when(videoProcessingTaskMapper.selectList(any())).thenReturn(List.of(expiredTask));
        when(videoProcessingTaskMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(videoMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(videoMapper.selectById(902L)).thenReturn(video);
        when(messageOutBoxMapper.insert(any(MessageOutbox.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, MessageOutbox.class).setId(1002L);
            return 1;
        });
        TransactionSynchronizationManager.initSynchronization();

        assertEquals(1, service.recoverExpiredProcessingTasks());

        verify(videoProcessingTaskMapper).update(any(LambdaUpdateWrapper.class));
        ArgumentCaptor<LambdaUpdateWrapper<Video>> videoCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(videoMapper).update(isNull(), videoCaptor.capture());
        assertTrue(videoCaptor.getValue().getParamNameValuePairs().containsValue(Video.VideoStatus.PENDING_REVIEW));
        ArgumentCaptor<MessageOutbox> outboxCaptor = ArgumentCaptor.forClass(MessageOutbox.class);
        verify(messageOutBoxMapper).insert(outboxCaptor.capture());
        assertEquals(902L, outboxCaptor.getValue().getBusinessId());
        assertEquals(MessageOutbox.OutboxStatus.PENDING, outboxCaptor.getValue().getStatus());
    }

    @Test
    void recoverExpiredProcessingTask_shouldRecoverOnlyAnExpiredLease() {
        VideoProcessingTaskServiceImpl service = new VideoProcessingTaskServiceImpl(
                videoProcessingTaskMapper, videoMapper, messageOutBoxMapper, new ObjectMapper(), outboxMessagePublisher, rabbitAdmin);
        VideoProcessingTask expiredTask = new VideoProcessingTask();
        expiredTask.setId(803L);
        expiredTask.setVideoId(903L);
        Video video = new Video();
        video.setId(903L);
        video.setUrl("2026/08/recovery.mp4");
        when(videoProcessingTaskMapper.selectOne(any())).thenReturn(expiredTask);
        when(videoProcessingTaskMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(videoMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(videoMapper.selectById(903L)).thenReturn(video);
        when(messageOutBoxMapper.insert(any(MessageOutbox.class))).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();

        assertTrue(service.recoverExpiredProcessingTask(903L));

        verify(videoProcessingTaskMapper).selectOne(any());
        verify(messageOutBoxMapper).insert(any(MessageOutbox.class));
    }
}
