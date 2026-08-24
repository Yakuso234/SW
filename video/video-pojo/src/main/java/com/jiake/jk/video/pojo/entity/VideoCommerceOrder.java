package com.jiake.jk.video.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video_commerce_order")
public class VideoCommerceOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long buyerId;
    private Long creatorId;
    private Long videoId;
    private Long productId;
    private Long flashSaleId;
    private Long userCouponId;
    private String productName;
    private Integer quantity;
    private Integer originalAmountCent;
    private Integer discountAmountCent;
    private Integer payableAmountCent;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private Status status;
    private LocalDateTime expireAt;
    private LocalDateTime paidAt;
    private LocalDateTime shippedAt;
    private LocalDateTime completedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING_PAYMENT(0), PAID(1), SHIPPED(2), COMPLETED(3), CANCELLED(4), EXPIRED(5), REFUND_REQUESTED(6), REFUNDED(7), REFUND_REJECTED(8);
        @EnumValue private final int code;
        Status(int code) { this.code = code; }
    }
}

