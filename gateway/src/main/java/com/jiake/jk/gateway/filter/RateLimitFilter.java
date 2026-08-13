package com.jiake.jk.gateway.filter;

import com.jiake.jk.gateway.properties.RateLimitProperties;
import com.jiake.jk.gateway.ratelimit.GatewayRateLimitService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/** Applies post-auth, distributed limits to mutating and AI paths. */
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final GatewayRateLimitService rateLimitService;
    private final RateLimitProperties properties;
    private final MeterRegistry meterRegistry;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled() || !rateLimitService.shouldProtect(
                exchange.getRequest().getMethod().name(), exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route == null ? null : route.getId();
        if (routeId == null) {
            routeId = exchange.getRequest().getPath().value();
        }
        String clientIp = exchange.getRequest().getRemoteAddress() == null
                ? "unknown" : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        String userId = exchange.getRequest().getHeaders().getFirst("id");
        return rateLimitService.check(routeId, clientIp, userId)
                .flatMap(decision -> {
                    if (decision.allowed()) {
                        exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
                        return chain.filter(exchange);
                    }
                    Counter.builder("sw.gateway.rate_limit.rejected")
                            .tag("reason", decision.reason())
                            .register(meterRegistry)
                            .increment();
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
                    exchange.getResponse().getHeaders().set("X-RateLimit-Reason", decision.reason());
                    exchange.getResponse().getHeaders().set("X-Trace-Id", traceId(exchange));
                    String body = "{\"code\":0,\"msg\":\"请求过于频繁，请稍后再试\",\"traceId\":\""
                            + traceId(exchange) + "\"}";
                    return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory()
                            .wrap(body.getBytes(StandardCharsets.UTF_8))));
                });
    }

    private String traceId(ServerWebExchange exchange) {
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        return traceId == null ? "" : traceId.replace("\"", "");
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
