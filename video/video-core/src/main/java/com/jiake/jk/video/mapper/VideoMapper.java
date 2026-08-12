package com.jiake.jk.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiake.jk.video.pojo.entity.Video;
import com.jiake.jk.video.pojo.entity.multi.VideoWithFavorite;
import com.jiake.jk.video.pojo.entity.multi.VideoWithLike;
import com.jiake.jk.video.pojo.mq.VideoCommentIncrMessage;
import com.jiake.jk.video.pojo.request.VideoInteractionBatchRequest;
import com.jiake.jk.video.pojo.response.VideoSearchResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface VideoMapper extends BaseMapper<Video> {
    @Select("select * from video limit 1")
    Video selectFirst();

    @Select("select url from video where id = #{id}")
    String selectVideoUrlById(Long id);

    void updateLikeBatch(List<VideoInteractionBatchRequest.Incr> likeIncrList);

    void updateFavoriteBatch(List<VideoInteractionBatchRequest.Incr> favoriteIncrList);

    void updateCommentBatch(List<VideoCommentIncrMessage.CommentIncr> commentIncrList);

    @Select("select status from video where id = #{id}")
    Video.VideoStatus selectVideoStatusUrlById(Long id);

    @Select("select id, url, cover_url, description, likes, comments, favorites, created_at from video where creator_id = #{userId} and status = 'UPLOADED' order by created_time desc")
    List<Video> selectUploadedVideoByUserId(Long userId);

    List<Video> selectPublishedVideoByUserId(Long userId, Long lastMinId);

    List<Video> selectProcessingVideoByUserId(Long userId, Long lastMinId);

    List<Video> selectRejectedVideoByUserId(Long userId, Long lastMinId);

    @Select("select count(*) from video where id = #{videoId}")
    boolean selectIsExistById(Long videoId);

    List<VideoWithLike> selectLikeVideoByUserId(Long userId, Long lastMinId);

    List<VideoWithFavorite> selectFavoriteVideoByUserId(Long userId, Long lastMinId);

    List<VideoSearchResponse> searchPublishedVideoByDescriptionPrefix(String keyword);
}
