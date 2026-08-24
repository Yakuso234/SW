package com.jiake.jk.video.service;

import com.jiake.jk.video.pojo.request.*;
import com.jiake.jk.video.pojo.response.*;

import java.util.List;

public interface VideoCommerceService {
    VideoProductCardResponse createProduct(Long creatorId, CreateVideoProductRequest request);
    VideoProductCardResponse createFlashSale(Long creatorId, CreateFlashSaleRequest request);
    UserCouponResponse createCouponTemplate(Long creatorId, CreateCouponTemplateRequest request);
    List<VideoProductCardResponse> getCreatorProducts(Long creatorId);
    VideoProductCardResponse getProductByVideo(Long videoId);
    List<UserCouponResponse> getClaimableCoupons(Long creatorId);
    void claimCoupon(Long userId, Long templateId);
    List<UserCouponResponse> getMyCoupons(Long userId);
    CommerceOrderResponse createOrder(Long buyerId, CreateCommerceOrderRequest request);
    List<CommerceOrderResponse> getMyOrders(Long buyerId);
    List<CommerceOrderResponse> getCreatorOrders(Long creatorId);
    void pay(Long buyerId, Long orderId);
    void cancel(Long buyerId, Long orderId);
    void ship(Long creatorId, Long orderId);
    void complete(Long buyerId, Long orderId);
    RefundResponse requestRefund(Long buyerId, Long orderId, CreateRefundRequest request);
    List<RefundResponse> getMyRefunds(Long buyerId);
    List<RefundResponse> getCreatorRefunds(Long creatorId);
    void reviewRefund(Long creatorId, Long refundId, ReviewRefundRequest request);
}

