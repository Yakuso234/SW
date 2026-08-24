package com.jiake.jk.video.pojo.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record CommerceOrderResponse(Long id, Long videoId, Long productId, String productName,
                                    Integer originalAmountCent, Integer discountAmountCent,
                                    Integer payableAmountCent, String status, LocalDateTime expireAt,
                                    LocalDateTime createdAt) {
}

