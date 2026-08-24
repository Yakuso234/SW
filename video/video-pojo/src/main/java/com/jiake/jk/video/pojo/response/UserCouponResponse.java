package com.jiake.jk.video.pojo.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record UserCouponResponse(Long id, Long templateId, String name, Integer thresholdCent,
                                 Integer discountCent, String status, LocalDateTime startsAt,
                                 LocalDateTime endsAt) {
}

