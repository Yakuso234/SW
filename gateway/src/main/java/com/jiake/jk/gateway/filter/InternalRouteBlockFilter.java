package com.jiake.jk.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/** Blocks service-to-service endpoints from being forwarded through the public gateway. */
@Component
public class InternalRouteBlockFilter implements GlobalFilter, Ordered {

    private static final String PRIVATE_API_SEGMENT = "/api/private/";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.contains(PRIVATE_API_SEGMENT)) {
            return chain.filter(exchange);
        }

        String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.NOT_FOUND);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (traceId != null && !traceId.isBlank()) {
            exchange.getResponse().getHeaders().set("X-Trace-Id", traceId);
        }
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(
                "{\"code\":0,\"msg\":\"Not Found\"}".getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
