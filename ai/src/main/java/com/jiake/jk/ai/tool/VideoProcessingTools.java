package com.jiake.jk.ai.tool;

import com.jiake.jk.common.response.Result;
import com.jiake.jk.video.feign.VideoPrivateClient;
import com.jiake.jk.video.pojo.entity.Video;
import com.jiake.jk.video.pojo.entity.VideoProcessingTask;
import com.jiake.jk.video.pojo.response.VideoProcessingStatusResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Locale;

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

    @Tool(name = "diagnose_video_processing_failure", description = "诊断当前创作者指定视频已经失败的异步处理任务。"
            + "仅当用户提供明确 videoId 并询问失败原因、转码失败或处理失败后的下一步时调用。"
            + "工具只返回基于真实失败摘要匹配出的诊断类别和建议，不提供写操作。")
    public String diagnoseVideoProcessingFailure(
            @ToolParam(description = "用户明确提供的视频ID") Long videoId,
            ToolContext toolContext) {
        Long creatorUserId = getRequiredUserId(toolContext);
        String traceId = String.valueOf(toolContext.getContext().getOrDefault(TRACE_ID_CONTEXT_KEY, ""));
        try {
            Result<VideoProcessingStatusResponse> result =
                    videoPrivateClient.getCreatorVideoProcessingStatus(creatorUserId, videoId);
            if (result == null || result.isError() || result.getData() == null) {
                log.warn("Video failure diagnosis returned business error, userId={}, videoId={}, traceId={}",
                        creatorUserId, videoId, traceId);
                return "未查询到该视频的可访问处理记录，不能据此诊断失败原因。";
            }
            VideoProcessingStatusResponse status = result.getData();
            if (!isFailed(status)) {
                log.info("Video failure diagnosis skipped because task is not failed, userId={}, videoId={}, traceId={}, videoStatus={}, processingStatus={}",
                        creatorUserId, videoId, traceId, status.videoStatus(), status.processingStatus());
                return "该视频当前不是失败状态；videoStatus=%s; processingStatus=%s。不能输出失败原因或建议。"
                        .formatted(status.videoStatus(), status.processingStatus());
            }
            String diagnosis = diagnose(status.errorMessage());
            log.info("Video failure diagnosis succeeded, userId={}, videoId={}, traceId={}",
                    creatorUserId, videoId, traceId);
            return ("videoId=%d; videoStatus=%s; processingStatus=%s; %s; diagnosisNote=诊断仅基于服务端失败摘要的规则匹配，"
                    + "未匹配时必须明确说明原因未知，不能编造。")
                    .formatted(status.videoId(), status.videoStatus(), status.processingStatus(), diagnosis);
        } catch (FeignException exception) {
            if (exception.status() >= 400 && exception.status() < 500) {
                log.info("Video failure diagnosis access was rejected, userId={}, videoId={}, traceId={}, status={}",
                        creatorUserId, videoId, traceId, exception.status());
                return "未查询到该视频的可访问处理记录，不能据此诊断失败原因。";
            }
            log.warn("Video failure diagnosis unavailable, userId={}, videoId={}, traceId={}, status={}",
                    creatorUserId, videoId, traceId, exception.status());
            return "视频处理状态服务暂不可用，不能据此编造失败原因。请稍后重试。";
        } catch (Exception exception) {
            log.warn("Video failure diagnosis unavailable, userId={}, videoId={}, traceId={}, type={}",
                    creatorUserId, videoId, traceId, exception.getClass().getSimpleName());
            return "视频处理状态服务暂不可用，不能据此编造失败原因。请稍后重试。";
        }
    }

    private boolean isFailed(VideoProcessingStatusResponse status) {
        return status.processingStatus() == VideoProcessingTask.ProcessingStatus.FAILED
                || status.videoStatus() == Video.VideoStatus.REJECTED;
    }

    private String diagnose(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "diagnosisCategory=UNKNOWN; diagnosisFact=任务已失败，但服务端没有提供可用错误摘要; "
                    + "recommendedActions=保留视频ID并联系平台支持排查，不要依据计数猜测原因";
        }
        String normalized = errorMessage.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "ffmpeg", "ffprobe", "codec", "decode", "moov atom", "invalid data")) {
            return "diagnosisCategory=MEDIA_TRANSCODING; diagnosisFact=失败摘要匹配媒体解析或转码关键词; "
                    + "recommendedActions=确认源文件可正常播放后重新上传，优先使用常见 H.264/AAC MP4 编码";
        }
        if (containsAny(normalized, "minio", "s3", "object storage", "access denied", "signature")) {
            return "diagnosisCategory=OBJECT_STORAGE; diagnosisFact=失败摘要匹配对象存储访问关键词; "
                    + "recommendedActions=稍后重试；若持续失败，联系平台检查上传对象与存储权限";
        }
        if (containsAny(normalized, "timeout", "timed out", "connection refused", "connect exception")) {
            return "diagnosisCategory=INFRASTRUCTURE_TRANSIENT; diagnosisFact=失败摘要匹配连接或超时关键词; "
                    + "recommendedActions=稍后再次查询处理状态；若仍失败，联系平台排查服务可用性";
        }
        return "diagnosisCategory=UNKNOWN; diagnosisFact=服务端失败摘要未匹配已知规则; "
                + "recommendedActions=保留视频ID并联系平台支持排查，不要编造失败原因";
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Long getRequiredUserId(ToolContext toolContext) {
        Object value = toolContext.getContext().get(USER_ID_CONTEXT_KEY);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Missing creator identity in tool context");
    }
}
