package com.jiake.jk.video.pojo.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;

/** 持久化的视频异步处理任务，租约过期后可由服务端恢复。 */
@Data
@TableName("video_processing_task")
public class VideoProcessingTask {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long videoId;
    private ProcessingStatus status;
    private Integer retryCount;
    private LocalDateTime leaseExpireAt;
    private String errorMessage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @Getter
    @AllArgsConstructor
    public enum ProcessingStatus {
        PENDING(0, "待处理"),
        PROCESSING(1, "处理中"),
        SUCCEEDED(2, "已成功"),
        FAILED(3, "已失败");

        @EnumValue
        private final int code;
        private final String desc;
    }
}
