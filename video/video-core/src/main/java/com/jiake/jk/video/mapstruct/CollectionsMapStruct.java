package com.jiake.jk.video.mapstruct;

import com.jiake.jk.video.pojo.entity.VideoUserCollections;
import com.jiake.jk.video.pojo.entity.multi.VideoCollectionsWithVideo;
import com.jiake.jk.video.pojo.request.PostCollectionsRequest;
import com.jiake.jk.video.pojo.request.PutCollectionsRequest;
import com.jiake.jk.video.pojo.response.GetCollectionsItemResponse;
import com.jiake.jk.video.pojo.response.GetCollectionsResponse;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface CollectionsMapStruct {
    CollectionsMapStruct INSTANCE = Mappers.getMapper(CollectionsMapStruct.class);

    GetCollectionsResponse toGetCollectionsResponse(VideoUserCollections videoUserCollections);

    GetCollectionsItemResponse toGetCollectionsItemResponse(VideoCollectionsWithVideo videoCollectionsWithVideo);

    VideoUserCollections toVideoUserCollections(PostCollectionsRequest postCollectionsRequest);

    VideoUserCollections toVideoUserCollections(PutCollectionsRequest putCollectionsRequest);
}
