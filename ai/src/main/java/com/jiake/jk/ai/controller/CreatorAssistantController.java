package com.jiake.jk.ai.controller;

import com.jiake.jk.ai.dto.CreatorAssistantChatRequest;
import com.jiake.jk.ai.service.CreatorAssistantService;
import com.jiake.jk.ai.utils.ReactiveUserContext;
import com.jiake.jk.common.exception.YHClientException;
import com.jiake.jk.common.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** Public entry for UI integration; identity is injected by Gateway as header id. */
@RestController
@RequestMapping("/creator-assistant")
@RequiredArgsConstructor
public class CreatorAssistantController {

    private final CreatorAssistantService creatorAssistantService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody CreatorAssistantChatRequest request,
                                                 ServerHttpRequest httpRequest,
                                                 ServerHttpResponse response) {
        String traceId = httpRequest.getHeaders().getFirst(TraceContext.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = java.util.UUID.randomUUID().toString().replace("-", "");
        }
        response.getHeaders().set(TraceContext.TRACE_ID_HEADER, traceId);
        return ReactiveUserContext.getUserId()
                .switchIfEmpty(reactor.core.publisher.Mono.error(new YHClientException("未识别到用户身份")))
                .flatMapMany(userId -> creatorAssistantService.stream(userId, traceId, request.message()))
                .map(event -> ServerSentEvent.<String>builder()
                        .event(event.event())
                        .data(event.data())
                        .build());
    }
}
