package com.jiake.jk.video.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 点赞、收藏事件的消费幂等记录。 */
@Data
@TableName("video_interaction_event_consumption")
public class VideoInteractionEventConsumption {
    private Long eventId;
}
