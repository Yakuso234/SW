package com.jiake.jk.video.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video_user_coupon")
public class VideoUserCoupon {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long templateId;
    private Long userId;
    private Status status;
    private Long lockedOrderId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum Status {
        AVAILABLE(0), LOCKED(1), USED(2), EXPIRED(3);
        @EnumValue private final int code;
        Status(int code) { this.code = code; }
    }
}

