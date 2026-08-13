package com.jiake.jk.video.mapper;

import com.jiake.jk.video.pojo.entity.VideoCommentEventConsumption;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VideoCommentEventConsumptionMapper {

    @Insert("insert ignore into video_comment_event_consumption (comment_id) values (#{commentId})")
    int insertIgnore(VideoCommentEventConsumption consumption);
}
