package com.jiake.jk.video.pojo.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video_product")
public class VideoProduct {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long videoId;
    private Long creatorId;
    private String name;
    private String description;
    private String imageUrl;
    private Integer priceCent;
    private Integer stock;
    private Integer soldCount;
    private Status status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum Status {
        OFF_SHELF(0), ON_SHELF(1);
        @EnumValue private final int code;
        Status(int code) { this.code = code; }
    }
}

