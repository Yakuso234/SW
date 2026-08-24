package com.jiake.jk.video.pojo.request;

public record CreateCommerceOrderRequest(Long flashSaleId, Long userCouponId,
                                         String receiverName, String receiverPhone, String receiverAddress) {
}

