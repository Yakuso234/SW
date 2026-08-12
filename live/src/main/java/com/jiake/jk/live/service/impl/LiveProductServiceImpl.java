package com.jiake.jk.live.service.impl;

import com.jiake.jk.common.exception.YHClientException;
import com.jiake.jk.common.utils.AWSUtils;
import com.jiake.jk.common.utils.SnowflakeUtils;
import com.jiake.jk.live.cache.LiveCache;
import com.jiake.jk.live.entity.LiveProduct;
import com.jiake.jk.live.mapper.LiveProductMapper;
import com.jiake.jk.live.request.PostLiveProductRequest;
import com.jiake.jk.live.response.LiveProductItemResponse;
import com.jiake.jk.live.service.LiveProductService;
import com.jiake.jk.live.websocket.pojo.LiveMessage;
import com.jiake.jk.product.feign.ProductPrivateClient;
import com.jiake.jk.product.pojo.response.GetOnSaleProductForLiveResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class LiveProductServiceImpl implements LiveProductService {

    @Autowired
    private LiveProductMapper liveProductMapper;
    @Autowired
    private SnowflakeUtils snowflakeUtils;
    @Autowired
    private AWSUtils awsUtils;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private LiveCache liveCache;
    @Autowired
    private ProductPrivateClient productPrivateClient;

    @Override
    public void postMerchantProduct(Long userId, PostLiveProductRequest postLiveProductRequest) {
        if (!Objects.equals(liveCache.getAnchorId(postLiveProductRequest.getRoomId()), userId)) {
            throw new YHClientException("你没有权限！");
        }

        Long productId = postLiveProductRequest.getProductId();
        Boolean isMerchantOnSaleProduct = productPrivateClient.isMerchantOnSaleProduct(userId, productId).getData();
        if (!Boolean.TRUE.equals(isMerchantOnSaleProduct)) {
            throw new YHClientException("商品不存在或未上架！");
        }

        LiveProduct liveProduct = new LiveProduct();
        liveProduct.setId(snowflakeUtils.nextId());
        liveProduct.setRoomId(postLiveProductRequest.getRoomId());
        liveProduct.setProductId(productId);

        liveProductMapper.insert(liveProduct);
        messagingTemplate.convertAndSend("/topic/room." + postLiveProductRequest.getRoomId(), new LiveMessage(LiveMessage.MessageType.PRODUCT, productId.toString()));
    }

    @Override
    public List<LiveProductItemResponse> getRoomLiveProduct(Long roomId) {
        List<Long> productIdList = liveProductMapper.selectProductIdsByRoomId(roomId);
        if (productIdList.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, GetOnSaleProductForLiveResponse> productMap = productPrivateClient.getProductsForLive(productIdList).getData();
        List<LiveProductItemResponse> responseList = productIdList.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .map(this::toLiveProductItemResponse)
                .toList();
        responseList.forEach(response -> response.setImageUrl(response.getImageUrl()));
        return responseList;
    }

    @Override
    public LiveProductItemResponse getLiveProduct(Long productId) {
        if (liveProductMapper.selectProductId(productId) == null) {
            return null;
        }

        Map<Long, GetOnSaleProductForLiveResponse> productMap = productPrivateClient.getProductsForLive(List.of(productId)).getData();
        GetOnSaleProductForLiveResponse product = productMap.get(productId);
        LiveProductItemResponse response = product == null ? null : toLiveProductItemResponse(product);
        if (response != null) {
            response.setImageUrl(response.getImageUrl());
        }
        return response;
    }

    private LiveProductItemResponse toLiveProductItemResponse(GetOnSaleProductForLiveResponse product) {
        return new LiveProductItemResponse(product.getProductId(), product.getTitle(), product.getImageUrl(), product.getSalesVolume());
    }
}
