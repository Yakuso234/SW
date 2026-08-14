package com.jiake.jk.ai.tool;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.video.feign.VideoPrivateClient;
import com.jiake.jk.video.pojo.entity.Video;
import com.jiake.jk.video.pojo.entity.VideoProcessingTask;
import com.jiake.jk.video.pojo.response.VideoProcessingStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class VideoProcessingToolsTest {

    @Test
    void queryStatus_shouldUseIdentityFromToolContextInsteadOfModelArgument() {
        VideoPrivateClient client = mock(VideoPrivateClient.class);
        VideoProcessingTools tools = new VideoProcessingTools(client);
        when(client.getCreatorVideoProcessingStatus(101L, 701L)).thenReturn(Result.success(
                new VideoProcessingStatusResponse(701L, Video.VideoStatus.PROCESSING,
                        VideoProcessingTask.ProcessingStatus.PROCESSING, 1, null, null, null)));

        String response = tools.queryVideoProcessingStatus(701L,
                new ToolContext(Map.of("creatorUserId", 101L, "traceId", "tool-trace-001")));

        assertTrue(response.contains("videoId=701"));
        assertTrue(response.contains("PROCESSING"));
        assertTrue(response.contains("仅系统累计计数"));
        assertTrue(response.contains("工具返回最近更新时间"));
        verify(client).getCreatorVideoProcessingStatus(101L, 701L);
    }

    @Test
    void queryStatus_shouldRejectMissingIdentityBeforeCallingVideoService() {
        VideoPrivateClient client = mock(VideoPrivateClient.class);
        VideoProcessingTools tools = new VideoProcessingTools(client);

        assertThrows(IllegalStateException.class,
                () -> tools.queryVideoProcessingStatus(702L, new ToolContext(Map.of())));

        verifyNoInteractions(client);
    }

    @Test
    void queryStatus_shouldReturnGenericNoAccessMessageForVideoClient4xx() {
        VideoPrivateClient client = mock(VideoPrivateClient.class);
        VideoProcessingTools tools = new VideoProcessingTools(client);
        when(client.getCreatorVideoProcessingStatus(102L, 702L))
                .thenThrow(new feign.FeignException.BadRequest(
                        "video not accessible",
                        feign.Request.create(feign.Request.HttpMethod.GET, "http://video/private", Map.of(), null, UTF_8, null),
                        null,
                        Map.of()));

        String response = tools.queryVideoProcessingStatus(702L,
                new ToolContext(Map.of("creatorUserId", 102L, "traceId", "tool-trace-002")));

        assertTrue(response.contains("可访问处理记录"));
        assertTrue(!response.contains("暂不可用"));
    }

    @Test
    void diagnoseFailure_shouldUseRuleMatchedCategoryWithoutReturningRawError() {
        VideoPrivateClient client = mock(VideoPrivateClient.class);
        VideoProcessingTools tools = new VideoProcessingTools(client);
        when(client.getCreatorVideoProcessingStatus(103L, 703L)).thenReturn(Result.success(
                new VideoProcessingStatusResponse(703L, Video.VideoStatus.REJECTED,
                        VideoProcessingTask.ProcessingStatus.FAILED, 1, null,
                        "ffmpeg decode failed at C:\\\\private-work\\\\source.mp4", null)));

        String response = tools.diagnoseVideoProcessingFailure(703L,
                new ToolContext(Map.of("creatorUserId", 103L, "traceId", "tool-trace-003")));

        assertTrue(response.contains("MEDIA_TRANSCODING"));
        assertTrue(response.contains("媒体解析或转码关键词"));
        assertTrue(!response.contains("private-work"));
        verify(client).getCreatorVideoProcessingStatus(103L, 703L);
    }

    @Test
    void diagnoseFailure_shouldRefuseToInferWhenTaskIsNotFailed() {
        VideoPrivateClient client = mock(VideoPrivateClient.class);
        VideoProcessingTools tools = new VideoProcessingTools(client);
        when(client.getCreatorVideoProcessingStatus(104L, 704L)).thenReturn(Result.success(
                new VideoProcessingStatusResponse(704L, Video.VideoStatus.PUBLISHED,
                        VideoProcessingTask.ProcessingStatus.SUCCEEDED, 0, null, null, null)));

        String response = tools.diagnoseVideoProcessingFailure(704L,
                new ToolContext(Map.of("creatorUserId", 104L, "traceId", "tool-trace-004")));

        assertTrue(response.contains("不是失败状态"));
        assertTrue(response.contains("不能输出失败原因"));
    }
}
