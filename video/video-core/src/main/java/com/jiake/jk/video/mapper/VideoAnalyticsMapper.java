package com.jiake.jk.video.mapper;

import com.jiake.jk.video.pojo.response.CreatorAnalyticsOverviewResponse;
import com.jiake.jk.video.pojo.response.CreatorAnalyticsTrendResponse;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface VideoAnalyticsMapper {

    @Select("select creator_id from video where id = #{videoId} and status = 5")
    Long selectPublishedCreatorId(@Param("videoId") Long videoId);

    @Insert("insert ignore into video_view_event (id, video_id, creator_id, viewer_id, viewed_date) values (#{id}, #{videoId}, #{creatorId}, #{viewerId}, #{viewedDate})")
    int insertDailyView(@Param("id") Long id, @Param("videoId") Long videoId,
                        @Param("creatorId") Long creatorId, @Param("viewerId") Long viewerId,
                        @Param("viewedDate") LocalDate viewedDate);

    @Update("update video set views = views + 1 where id = #{videoId} and status = 5")
    int incrementView(@Param("videoId") Long videoId);

    @Select("""
            select count(*) as publishedCount,
                   coalesce(sum(views), 0) as views,
                   coalesce(sum(likes), 0) as likes,
                   coalesce(sum(comments), 0) as comments,
                   coalesce(sum(favorites), 0) as favorites
            from video
            where creator_id = #{creatorId} and status = 5
            """)
    CreatorAnalyticsOverviewResponse selectOverview(@Param("creatorId") Long creatorId);

    @Select("""
            select date_format(viewed_date, '%Y-%m-%d') as date, count(*) as views
            from video_view_event
            where creator_id = #{creatorId} and viewed_date >= #{fromDate}
            group by viewed_date
            order by viewed_date asc
            """)
    List<CreatorAnalyticsTrendResponse> selectDailyViews(@Param("creatorId") Long creatorId,
                                                          @Param("fromDate") LocalDate fromDate);
}
