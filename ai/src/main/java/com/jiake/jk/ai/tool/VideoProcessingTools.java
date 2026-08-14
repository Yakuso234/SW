package com.jiake.jk.ai.tool;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.video.feign.VideoPrivateClient;
import com.jiake.jk.video.pojo.response.VideoProcessingStatusResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Read-only video facts for the creator assistant. Identity comes from ToolContext, never model input. */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoProcessingTools {

    private static final String USER_ID_CONTEXT_KEY = "creatorUserId";
    private static final String TRACE_ID_CONTEXT_KEY = "traceId";

    private final VideoPrivateClient videoPrivateClient;

    @Tool(name = "query_video_processing_status", description = "查询当前创作者指定视频的异步处理状态、重试次数和失败摘要。"
            + "仅当用户询问某个明确 videoId 的上传、转码、审核或处理进度时调用。")
    public String queryVideoProcessingStatus(
            @ToolParam(description = "用户明确提供的视频ID") Long videoId,
            ToolContext toolContext) {
        Long creatorUserId = getRequiredUserId(toolContext);
        String traceId = String.valueOf(toolContext.getContext().getOrDefault(TRACE_ID_CONTEXT_KEY, ""));
        try {
            Result<VideoProcessingStatusResponse> result =
                    videoPrivateClient.getCreatorVideoProcessingStatus(creatorUserId, videoId);
            if (result == null || result.isError() || result.getData() == null) {
                log.warn("Video processing tool returned business error, userId={}, videoId={}, traceId={}",
                        creatorUserId, videoId, traceId);
                return "未查询到该视频的可访问处理记录。视频可能不存在、无访问权限或尚未创建处理任务。";
            }
            VideoProcessingStatusResponse status = result.getData();
            log.info("Video processing tool succeeded, userId={}, videoId={}, traceId={}",
                    creatorUserId, videoId, traceId);
            return "videoId=%d; videoStatus=%s; processingStatus=%s; retryCount=%s; retryCountNote=仅系统累计计数，未提供原因，禁止基于该字段推断异常或重试过程; leaseExpireAt=%s; errorMessage=%s; updatedAt=%s; updatedAtNote=输出时只能使用“工具返回最近更新时间：字段值”，不得追加解释"
                    .formatted(status.videoId(), status.videoStatus(), status.processingStatus(), status.retryCount(),
                            status.leaseExpireAt(), status.errorMessage(), status.updatedAt());
        } catch (FeignException exception) {
            if (exception.status() >= 400 && exception.status() < 500) {
                log.info("Video processing tool access was rejected, userId={}, videoId={}, traceId={}, status={}",
                        creatorUserId, videoId, traceId, exception.status());
                return "未查询到该视频的可访问处理记录。视频可能不存在、无访问权限或尚未创建处理任务。";
            }
            log.warn("Video processing tool unavailable, userId={}, videoId={}, traceId={}, status={}",
                    creatorUserId, videoId, traceId, exception.status());
            return "视频处理状态服务暂不可用，不能据此编造进度或失败原因。请稍后重试。";
        } catch (Exception exception) {
            log.warn("Video processing tool unavailable, userId={}, videoId={}, traceId={}, type={}",
                    creatorUserId, videoId, traceId, exception.getClass().getSimpleName());
            return "视频处理状态服务暂不可用，不能据此编造进度或失败原因。请稍后重试。";
        }
    }

    private Long getRequiredUserId(ToolContext toolContext) {
        Object value = toolContext.getContext().get(USER_ID_CONTEXT_KEY);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Missing creator identity in tool context");
    }
}
