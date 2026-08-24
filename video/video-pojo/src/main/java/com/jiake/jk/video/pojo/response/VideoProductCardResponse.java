package com.jiake.jk.video.pojo.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record VideoProductCardResponse(Long productId, Long videoId, String name, String description,
                                       String imageUrl, Integer originalPriceCent, Integer salePriceCent,
                                       Integer remainingStock, Long flashSaleId, Integer perUserLimit,
                                       LocalDateTime startsAt, LocalDateTime endsAt, String activityStatus) {
}

