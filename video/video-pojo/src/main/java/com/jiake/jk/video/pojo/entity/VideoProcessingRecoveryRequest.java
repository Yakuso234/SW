package com.jiake.jk.video.pojo.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;

/** 跨服务视频处理恢复请求的持久化幂等回执。 */
@Data
@TableName("video_processing_recovery_request")
public class VideoProcessingRecoveryRequest {

    @TableId(type = IdType.INPUT)
    private Long id;
    private String idempotencyKey;
    private Long videoId;
    private String requestedBy;
    private String traceId;
    private RecoveryStatus status;
    private RecoveryReason reason;
    private Long outboxId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @AllArgsConstructor
    public enum RecoveryStatus {
        PENDING("PENDING"),
        ACCEPTED("ACCEPTED"),
        REJECTED("REJECTED");

        @EnumValue
        private final String value;
    }

    @Getter
    @AllArgsConstructor
    public enum RecoveryReason {
        PRECONDITION_NOT_MET("PRECONDITION_NOT_MET");

        @EnumValue
        private final String value;
    }
}
