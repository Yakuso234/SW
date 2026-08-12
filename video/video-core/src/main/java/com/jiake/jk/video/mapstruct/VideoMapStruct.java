package com.jiake.jk.video.mapstruct;

import com.jiake.jk.video.pojo.entity.Video;
import com.jiake.jk.video.pojo.entity.multi.VideoWithFavorite;
import com.jiake.jk.video.pojo.entity.multi.VideoWithInteractionStatus;
import com.jiake.jk.video.pojo.entity.multi.VideoWithLike;
import com.jiake.jk.video.pojo.response.*;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface VideoMapStruct {

    VideoMapStruct INSTANCE = Mappers.getMapper(VideoMapStruct.class);

    GetUploadedVideoResponse toGetUploadedVideoResponse(Video video);
    GetPublishedVideoResponse toGetPublishedVideoResponse(Video video);
    GetProcessingVideoResponse toGetProcessingVideoResponse(Video video);
    GetRejectedVideoResponse toGetRejectedVideoResponse(Video video);
    GetLikeVideoResponse toGetLikeVideoResponse(VideoWithLike videoWithLike);
    GetFavoriteVideoResponse toGetFavoriteVideoResponse(VideoWithFavorite videoWithFavorite);
    VideoMainResponse toVideoMainResponse(VideoWithInteractionStatus videoWithInteractionStatus);
}
