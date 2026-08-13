package com.jiake.jk.gateway.ratelimit;

import com.jiake.jk.gateway.properties.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayRateLimitServiceTest {

    @Test
    void check_shouldExposeRouteReasonWhenRequestHasNoUserDimension() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), anyList())).thenReturn(Flux.just(List.of(0L, 0L, 2500L, 2L)));
        GatewayRateLimitService service = new GatewayRateLimitService(redisTemplate, properties());

        StepVerifier.create(service.check("videoService1", "127.0.0.1", null))
                .expectNextMatches(decision -> !decision.allowed()
                        && "route".equals(decision.reason())
                        && decision.retryAfterSeconds() == 3)
                .verifyComplete();
    }

    @Test
    void check_shouldFailClosedWhenRedisIsUnavailable() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), anyList())).thenReturn(Flux.error(new IllegalStateException("redis down")));
        GatewayRateLimitService service = new GatewayRateLimitService(redisTemplate, properties());

        StepVerifier.create(service.check("videoService1", "127.0.0.1", "900001"))
                .expectNextMatches(decision -> !decision.allowed() && "redis-unavailable".equals(decision.reason()))
                .verifyComplete();
    }

    @Test
    void shouldProtect_shouldCoverWritesAndAllAiPaths() {
        GatewayRateLimitService service = new GatewayRateLimitService(mock(ReactiveStringRedisTemplate.class), properties());

        org.junit.jupiter.api.Assertions.assertTrue(service.shouldProtect("POST", "/video/api/me"));
        org.junit.jupiter.api.Assertions.assertTrue(service.shouldProtect("GET", "/ai/api/public/ask"));
        org.junit.jupiter.api.Assertions.assertFalse(service.shouldProtect("GET", "/video/api/public/feed"));
    }

    private RateLimitProperties properties() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setRouteLimits(Map.of());
        return properties;
    }
}
