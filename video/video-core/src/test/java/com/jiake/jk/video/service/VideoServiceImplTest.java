package com.jiake.jk.video.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiake.jk.common.utils.AWSUtils;
import com.jiake.jk.video.mapper.MessageOutBoxMapper;
import com.jiake.jk.video.mapper.VideoMapper;
import com.jiake.jk.video.mapper.VideoTagMapper;
import com.jiake.jk.video.mapper.VideoTagMpMapper;
import com.jiake.jk.video.mapper.VideoUploadTaskMapper;
import com.jiake.jk.video.pojo.entity.MessageOutbox;
import com.jiake.jk.video.pojo.entity.Video;
import com.jiake.jk.video.pojo.entity.VideoUploadTask;
import com.jiake.jk.video.pojo.mq.VideoReviewMessage;
import com.jiake.jk.video.pojo.request.GetPresignUrlRequest;
import com.jiake.jk.video.pojo.request.PostVideoMessageRequest;
import com.jiake.jk.video.service.impl.VideoServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoServiceImplTest {

    @BeforeAll
    static void initializeMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "VideoServiceImplTest");
        TableInfoHelper.initTableInfo(assistant, Video.class);
        TableInfoHelper.initTableInfo(assistant, VideoUploadTask.class);
    }

    @Mock private VideoMapper videoMapper;
    @Mock private VideoTagMapper videoTagMapper;
    @Mock private VideoTagMpMapper videoTagMpMapper;
    @Mock private VideoUploadTaskMapper videoUploadTaskMapper;
    @Mock private MessageOutBoxMapper messageOutBoxMapper;
    @Mock private AWSUtils awsUtils;
    @Mock private MultipartFile cover;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void postVideoMessage_shouldPersistVideoIdAndMoveToPendingReview() throws Exception {
        VideoServiceImpl service = newService();
        Long userId = 101L;
        Long videoId = 202L;
        Video video = new Video();
        video.setId(videoId);
        video.setCreatorId(userId);
        video.setUrl("2026/08/video.mp4");
        video.setStatus(Video.VideoStatus.DRAFT);
        when(videoMapper.selectOne(any())).thenReturn(video);
        when(videoMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(videoTagMapper.selectSimplyByNames(List.of())).thenReturn(List.of());
        when(messageOutBoxMapper.insert(any(MessageOutbox.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, MessageOutbox.class).setId(303L);
            return 1;
        });
        TransactionSynchronizationManager.initSynchronization();

        PostVideoMessageRequest request = new PostVideoMessageRequest();
        request.setVideoId(videoId);
        request.setDescription("测试视频");
        request.setAddedTagList(List.of());
        request.setCover(cover);

        service.postVideoMessage(userId, request);

        ArgumentCaptor<LambdaUpdateWrapper<Video>> updateCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(videoMapper).update(isNull(), updateCaptor.capture());
        assertTrue(updateCaptor.getValue().getSqlSet().contains("status"));
        assertTrue(updateCaptor.getValue().getSqlSegment().contains("status"));
        assertTrue(updateCaptor.getValue().getParamNameValuePairs()
                .containsValue(Video.VideoStatus.PENDING_REVIEW));

        ArgumentCaptor<MessageOutbox> outboxCaptor = ArgumentCaptor.forClass(MessageOutbox.class);
        verify(messageOutBoxMapper).insert(outboxCaptor.capture());
        MessageOutbox outbox = outboxCaptor.getValue();
        assertEquals(videoId, outbox.getBusinessId());
        assertEquals(MessageOutbox.OutboxStatus.PENDING, outbox.getStatus());
        VideoReviewMessage message = objectMapper.readValue(outbox.getMessageBody(), VideoReviewMessage.class);
        assertEquals(videoId, message.getVideoId());
        assertEquals(video.getUrl(), message.getVideoUrl());
    }

    @Test
    void presignUploadPart_shouldUpdateOnlyCurrentUploadingTask() {
        VideoServiceImpl service = newService();
        VideoUploadTask task = new VideoUploadTask();
        task.setId(404L);
        task.setUserId(101L);
        task.setKey("2026/08/video.mp4");
        task.setUploadId("upload-1");
        task.setStatus(VideoUploadTask.UploadStatus.UPLOADING);
        when(videoUploadTaskMapper.selectForPresign(404L)).thenReturn(task);
        when(awsUtils.presignUploadPart(eq(task.getKey()), eq(task.getUploadId()), eq(List.of(1)), any())).thenReturn(Map.of(1, "https://example.test/part-1"));

        service.presignUploadPart(101L, new GetPresignUrlRequest(404L, List.of(1)));

        ArgumentCaptor<LambdaUpdateWrapper<VideoUploadTask>> updateCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(videoUploadTaskMapper).update(updateCaptor.capture());
        String sqlSegment = updateCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("id"));
        assertTrue(sqlSegment.contains("status"));
        assertTrue(updateCaptor.getValue().getParamNameValuePairs()
                .containsValue(VideoUploadTask.UploadStatus.UPLOADING));
        verify(awsUtils).presignUploadPart(eq(task.getKey()), eq(task.getUploadId()), eq(List.of(1)), any());
    }

    @Test
    void transitionVideoStatus_shouldUseExpectedStatusAsConcurrencyGuard() {
        VideoServiceImpl service = newService();
        when(videoMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        assertTrue(service.transitionVideoStatus(505L, Video.VideoStatus.PENDING_REVIEW, Video.VideoStatus.PROCESSING));

        ArgumentCaptor<LambdaUpdateWrapper<Video>> updateCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(videoMapper).update(isNull(), updateCaptor.capture());
        assertTrue(updateCaptor.getValue().getSqlSegment().contains("id"));
        assertTrue(updateCaptor.getValue().getSqlSegment().contains("status"));
        assertTrue(updateCaptor.getValue().getParamNameValuePairs().containsValue(Video.VideoStatus.PROCESSING));
    }

    @Test
    void putVideoStatusToPublished_shouldRequireProcessingStatus() {
        VideoServiceImpl service = newService();
        when(videoMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        service.putVideoStatusToPublished(506L);

        ArgumentCaptor<LambdaUpdateWrapper<Video>> updateCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(videoMapper).update(isNull(), updateCaptor.capture());
        assertTrue(updateCaptor.getValue().getParamNameValuePairs().containsValue(Video.VideoStatus.PUBLISHED));
        assertTrue(updateCaptor.getValue().getSqlSegment().contains("status"));
    }

    private VideoServiceImpl newService() {
        VideoServiceImpl service = new VideoServiceImpl();
        ReflectionTestUtils.setField(service, "videoMapper", videoMapper);
        ReflectionTestUtils.setField(service, "videoTagMapper", videoTagMapper);
        ReflectionTestUtils.setField(service, "videoTagMpMapper", videoTagMpMapper);
        ReflectionTestUtils.setField(service, "videoUploadTaskMapper", videoUploadTaskMapper);
        ReflectionTestUtils.setField(service, "messageOutBoxMapper", messageOutBoxMapper);
        ReflectionTestUtils.setField(service, "awsUtils", awsUtils);
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        return service;
    }
}
