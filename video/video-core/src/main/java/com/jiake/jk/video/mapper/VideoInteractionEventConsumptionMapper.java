package com.jiake.jk.video.mapper;

import com.jiake.jk.video.pojo.entity.VideoInteractionEventConsumption;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VideoInteractionEventConsumptionMapper {

    @Insert("insert ignore into video_interaction_event_consumption (event_id) values (#{eventId})")
    int insertIgnore(VideoInteractionEventConsumption consumption);
}
