package com.jiake.jk.video.pojo.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jiake.jk.video.pojo.entity.Video;
import com.jiake.jk.video.pojo.entity.VideoProcessingTask;

import java.time.LocalDateTime;

/** Read-only, creator-scoped snapshot of asynchronous video processing. */
public record VideoProcessingStatusResponse(
        Long videoId,
        Video.VideoStatus videoStatus,
        VideoProcessingTask.ProcessingStatus processingStatus,
        Integer retryCount,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime leaseExpireAt,
        String errorMessage,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime updatedAt) {
}
