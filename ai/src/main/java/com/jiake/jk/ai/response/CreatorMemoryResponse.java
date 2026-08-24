package com.jiake.jk.ai.response;

import java.time.LocalDateTime;

public record CreatorMemoryResponse(Long id, String type, String content, LocalDateTime createdAt) {
}

