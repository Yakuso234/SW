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
import com.jiake.jk.video.pojo.mq.VideoPublishedMessage;
import com.jiake.jk.video.constant.RabbitMQConstant;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoProcessingTaskServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

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
                videoProcessingTaskMapper, videoMapper, messageOutBoxMapper, objectMapper, outboxMessagePublisher, rabbitAdmin);
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
                videoProcessingTaskMapper, videoMapper, messageOutBoxMapper, objectMapper, outboxMessagePublisher, rabbitAdmin);
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
    void recoverExpiredProcessingTasks_shouldSkipScheduledRecoveryWhenDisabled() {
        VideoProcessingTaskServiceImpl service = new VideoProcessingTaskServiceImpl(
                videoProcessingTaskMapper, videoMapper, messageOutBoxMapper, objectMapper, outboxMessagePublisher, rabbitAdmin);
        ReflectionTestUtils.setField(service, "automaticRecoveryEnabled", false);

        assertEquals(0, service.recoverExpiredProcessingTasks());

        verify(videoProcessingTaskMapper, never()).selectList(any());
        verify(messageOutBoxMapper, never()).insert(any());
    }

    @Test
    void recoverExpiredProcessingTask_shouldRecoverOnlyAnExpiredLease() {
        VideoProcessingTaskServiceImpl service = new VideoProcessingTaskServiceImpl(
                videoProcessingTaskMapper, videoMapper, messageOutBoxMapper, objectMapper, outboxMessagePublisher, rabbitAdmin);
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

    @Test
    void completeVideoProcessing_shouldPersistPublishedAtWithPublishedStatus() throws Exception {
        VideoProcessingTaskServiceImpl service = new VideoProcessingTaskServiceImpl(
                videoProcessingTaskMapper, videoMapper, messageOutBoxMapper, objectMapper, outboxMessagePublisher, rabbitAdmin);
        when(videoProcessingTaskMapper.update(any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(videoMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        Video publishedVideo = new Video();
        publishedVideo.setId(904L);
        publishedVideo.setCreatorId(905L);
        publishedVideo.setPublishedAt(java.time.LocalDateTime.now());
        when(videoMapper.selectById(904L)).thenReturn(publishedVideo);
        when(messageOutBoxMapper.insert(any(MessageOutbox.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, MessageOutbox.class).setId(906L);
            return 1;
        });
        TransactionSynchronizationManager.initSynchronization();

        service.completeVideoProcessing(904L, "processed/904.mp4", "processed/904.jpg");

        ArgumentCaptor<LambdaUpdateWrapper<Video>> videoCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(videoMapper, times(1)).update(isNull(), videoCaptor.capture());
        assertTrue(videoCaptor.getValue().getParamNameValuePairs()
                .containsValue(Video.VideoStatus.PUBLISHED));
        assertTrue(videoCaptor.getValue().getSqlSet().contains("published_at"));
        ArgumentCaptor<MessageOutbox> outboxCaptor = ArgumentCaptor.forClass(MessageOutbox.class);
        verify(messageOutBoxMapper).insert(outboxCaptor.capture());
        assertEquals(RabbitMQConstant.VIDEO_PUBLISH_INBOX_QUEUE, outboxCaptor.getValue().getRoutingKey());
        VideoPublishedMessage event = objectMapper.readValue(
                outboxCaptor.getValue().getMessageBody(), VideoPublishedMessage.class);
        assertEquals(904L, event.getVideoId());
        assertEquals(905L, event.getCreatorId());
        assertDoesNotThrow(() -> java.time.LocalDateTime.parse(event.getPublishedAt()));
    }
}
