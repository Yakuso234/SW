package com.jiake.jk.video.mapper.multi;

import com.jiake.jk.video.pojo.entity.multi.VideoWithInteractionStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

import java.util.List;

@Mapper
public interface VideoMultiMapper {
    List<VideoWithInteractionStatus> selectMainRandom(@Param("userId") Long userId);

    VideoWithInteractionStatus selectMainOne(@Param("videoId") Long videoId, @Param("userId") Long userId);

    List<VideoWithInteractionStatus> selectPublishedFeed(@Param("userId") Long userId,
                                                         @Param("publishedAt") LocalDateTime publishedAt,
                                                         @Param("videoId") Long videoId,
                                                         @Param("limit") int limit);

    List<VideoWithInteractionStatus> selectFollowFeed(@Param("recipientId") Long recipientId,
                                                       @Param("publishedAt") LocalDateTime publishedAt,
                                                       @Param("feedId") Long feedId,
                                                       @Param("limit") int limit);
}
