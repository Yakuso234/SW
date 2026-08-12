package com.jiake.jk.ai.service;

import reactor.core.publisher.Flux;

public interface LLMService {
    Flux<Object> getLLMResponse(Long userId, Long conversationId, String msg, boolean enableThinking);
}
