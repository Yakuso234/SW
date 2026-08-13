package com.jiake.jk.video.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** Records consumed comment events so redelivery does not increment counters twice. */
@Data
@TableName("video_comment_event_consumption")
public class VideoCommentEventConsumption {
    @TableId
    private Long commentId;
}
