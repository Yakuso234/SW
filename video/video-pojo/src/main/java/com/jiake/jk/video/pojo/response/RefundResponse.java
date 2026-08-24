package com.jiake.jk.video.pojo.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record RefundResponse(Long id, Long orderId, String reason, String status,
                             String reply, LocalDateTime createdAt) {
}

