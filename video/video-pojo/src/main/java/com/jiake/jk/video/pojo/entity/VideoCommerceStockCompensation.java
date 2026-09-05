package com.jiake.jk.video.pojo.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 已提交订单关闭后，驱动 Redis 秒杀库存回补的持久化任务。
 */
@Data
@TableName("video_commerce_stock_compensation")
public class VideoCommerceStockCompensation {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private Long flashSaleId;
    private Status status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime leaseExpireAt;
    private LocalDateTime completedAt;
    private String lastError;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING(0), PROCESSING(1), SUCCESS(2), FAILED(3);

        @EnumValue
        private final int code;

        Status(int code) {
            this.code = code;
        }
    }
}
