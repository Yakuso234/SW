package com.jiake.jk.video.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video_refund_request")
public class VideoRefundRequest {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private Long buyerId;
    private Long creatorId;
    private String reason;
    private Status status;
    private String reply;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING(0), APPROVED(1), REJECTED(2);
        @EnumValue private final int code;
        Status(int code) { this.code = code; }
    }
}
