package com.jiake.jk.video.pojo.request;

import java.time.LocalDateTime;

public record CreateFlashSaleRequest(Long productId, Integer salePriceCent, Integer totalStock,
                                     Integer perUserLimit, LocalDateTime startsAt, LocalDateTime endsAt) {
}

