package com.jiake.jk.video.pojo.request;

import java.time.LocalDateTime;

public record CreateCouponTemplateRequest(String name, Integer thresholdCent, Integer discountCent,
                                          Integer totalStock, LocalDateTime startsAt, LocalDateTime endsAt) {
}

