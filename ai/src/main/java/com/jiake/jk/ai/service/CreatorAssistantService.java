package com.jiake.jk.ai.service;

import reactor.core.publisher.Flux;

public interface CreatorAssistantService {
    Flux<StreamEvent> stream(Long userId, String traceId, String message);

    record StreamEvent(String event, String data) {
    }
}
