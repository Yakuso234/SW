package com.jiake.jk.live.service;

import com.jiake.jk.live.request.PostLiveProductRequest;
import com.jiake.jk.live.response.LiveProductItemResponse;

import java.util.List;

public interface LiveProductService {
    void postMerchantProduct(Long userId, PostLiveProductRequest postLiveProductRequest);

    List<LiveProductItemResponse> getRoomLiveProduct(Long roomId);

    LiveProductItemResponse getLiveProduct(Long productId);
}
