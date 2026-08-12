package com.jiake.jk.video.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jiake.jk.video.pojo.entity.VideoUserCollections;
import com.jiake.jk.video.pojo.request.DeleteCollectionsItemRequest;
import com.jiake.jk.video.pojo.request.PostCollectionsRequest;
import com.jiake.jk.video.pojo.request.PutCollectionsRequest;
import com.jiake.jk.video.pojo.request.TransferCollectionsItemRequest;
import com.jiake.jk.video.pojo.response.GetCollectionsItemResponse;
import com.jiake.jk.video.pojo.response.GetCollectionsResponse;

import java.util.List;
import java.util.Map;

public interface CollectionsService extends IService<VideoUserCollections> {
    List<GetCollectionsResponse> getCollections(Long userId, Long lastMinId);

    List<GetCollectionsItemResponse> getCollectionsItemList(Long userId, Long collectionsId, Long lastMinId);

    String postCollections(Long userId, PostCollectionsRequest postCollectionsRequest);

    void putCollections(Long userId, Long collectionsId, PutCollectionsRequest putCollectionsRequest);

    void deleteCollections(Long userId, Long collectionsId);

    Map<Long, Long> getDefaultCollectionsIdBatch(List<Long> list);

    void deleteCollectionsItemBatch(Long userId, DeleteCollectionsItemRequest deleteCollectionsItemRequest);

    void moveCollectionsItemBatch(Long userId, TransferCollectionsItemRequest transferCollectionsItemRequest);

    void copyCollectionsItemBatch(Long userId, TransferCollectionsItemRequest transferCollectionsItemRequest);
}
