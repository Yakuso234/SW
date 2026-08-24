package com.jiake.jk.ai.service;

import com.jiake.jk.ai.dto.SaveCreatorMemoryRequest;
import com.jiake.jk.ai.response.CreatorMemoryResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CreatorMemoryService {
    Flux<CreatorMemoryResponse> list(Long userId);
    Mono<CreatorMemoryResponse> save(Long userId, SaveCreatorMemoryRequest request);
    Mono<Void> delete(Long userId, Long memoryId);
    Mono<String> buildContext(Long userId, String message);
    Mono<Void> appendSessionTurn(Long userId, String userMessage, String assistantMessage);
}

