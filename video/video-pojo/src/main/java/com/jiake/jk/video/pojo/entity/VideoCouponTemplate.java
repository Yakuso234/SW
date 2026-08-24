package com.jiake.jk.video.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video_coupon_template")
public class VideoCouponTemplate {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long creatorId;
    private String name;
    private Integer thresholdCent;
    private Integer discountCent;
    private Integer totalStock;
    private Integer claimedCount;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Status status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum Status {
        CLOSED(0), ACTIVE(1);
        @EnumValue private final int code;
        Status(int code) { this.code = code; }
    }
}

