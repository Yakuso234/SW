package com.jiake.jk.ai.service;

import reactor.core.publisher.Flux;

public interface CustomerService {
    Flux<Object> chat(Long userId, Long conversationId, String msg, boolean enableThinking);
}
