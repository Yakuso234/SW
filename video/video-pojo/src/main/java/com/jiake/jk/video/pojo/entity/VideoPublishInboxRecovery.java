package com.jiake.jk.video.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Records a manual replay prepared from the follow-feed dead-letter queue. */
@Data
@TableName("video_publish_inbox_recovery")
public class VideoPublishInboxRecovery {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String messageDigest;
    private Long videoId;
    private Long creatorId;
    private String traceId;
    private Integer recoveryAttempt;
    private Long outboxId;
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
}
