package com.jiake.jk.gateway.ratelimit;

import com.jiake.jk.gateway.properties.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/** Executes an all-or-nothing, multi-key sliding-window check in one Redis Lua script. */
@Service
@RequiredArgsConstructor
public class GatewayRateLimitService {

    private static final String KEY_PREFIX = "sw:gateway:rate-limit:";
    private static final DefaultRedisScript<List> SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>("""
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local member = ARGV[3]
            local minRemaining = -1
            for i = 1, #KEYS do
                local limit = tonumber(ARGV[3 + i])
                redis.call('ZREMRANGEBYSCORE', KEYS[i], 0, now - window)
                local current = redis.call('ZCARD', KEYS[i])
                if current >= limit then
                    local oldest = redis.call('ZRANGE', KEYS[i], 0, 0, 'WITHSCORES')
                    local retry = window
                    if #oldest >= 2 then
                        retry = math.max(1, window - (now - tonumber(oldest[2])))
                    end
                    return {0, 0, retry, i}
                end
                local remaining = limit - current - 1
                if minRemaining == -1 or remaining < minRemaining then
                    minRemaining = remaining
                end
            end
            for i = 1, #KEYS do
                redis.call('ZADD', KEYS[i], now, member .. ':' .. i)
                redis.call('PEXPIRE', KEYS[i], window)
            end
            return {1, minRemaining, 0, 0}
            """, List.class);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    public Mono<RateLimitDecision> check(String routeId, String clientIp, String userId) {
        int windowMillis = Math.max(1, properties.getWindowSeconds()) * 1000;
        String normalizedRoute = routeId.replaceAll("[^a-zA-Z0-9:_-]", "_");
        List<String> keys = new ArrayList<>();
        List<String> limits = new ArrayList<>();
        keys.add(KEY_PREFIX + "ip:" + normalizedRoute + ':' + clientIp);
        limits.add(String.valueOf(properties.getIpLimit()));
        if (userId != null && !userId.isBlank()) {
            keys.add(KEY_PREFIX + "user:" + normalizedRoute + ':' + userId);
            limits.add(String.valueOf(properties.getUserLimit()));
        }
        keys.add(KEY_PREFIX + "route:" + normalizedRoute);
        limits.add(String.valueOf(properties.getRouteLimits().getOrDefault(routeId, properties.getRouteLimit())));

        String requestMember = java.util.UUID.randomUUID().toString();
        List<String> args = new ArrayList<>(List.of(String.valueOf(System.currentTimeMillis()),
                String.valueOf(windowMillis), requestMember));
        args.addAll(limits);
        return redisTemplate.execute(SLIDING_WINDOW_SCRIPT, keys, args)
                .singleOrEmpty()
                .map(result -> toDecision(result, userId != null && !userId.isBlank()))
                .defaultIfEmpty(RateLimitDecision.rejected("redis-empty-result", 1))
                .onErrorReturn(RateLimitDecision.rejected("redis-unavailable", 1));
    }

    public boolean shouldProtect(String method, String path) {
        if (properties.getProtectedMethods().stream().anyMatch(value -> value.equalsIgnoreCase(method))) {
            return true;
        }
        return properties.getProtectedPathPrefixes().stream().anyMatch(path::startsWith);
    }

    private RateLimitDecision toDecision(List result, boolean hasUserDimension) {
        if (result.size() < 4) {
            return RateLimitDecision.rejected("invalid-redis-result", 1);
        }
        long allowed = asLong(result.get(0));
        if (allowed == 1) {
            return RateLimitDecision.allowed(Math.max(0, asLong(result.get(1))));
        }
        long dimension = asLong(result.get(3));
        String reason = switch ((int) dimension) {
            case 1 -> "ip";
            case 2 -> hasUserDimension ? "user" : "route";
            case 3 -> "route";
            default -> "unknown";
        };
        long retrySeconds = Math.max(1, (asLong(result.get(2)) + 999) / 1000);
        return RateLimitDecision.rejected(reason, retrySeconds);
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    public record RateLimitDecision(boolean allowed, long remaining, String reason, long retryAfterSeconds) {
        static RateLimitDecision allowed(long remaining) {
            return new RateLimitDecision(true, remaining, null, 0);
        }

        static RateLimitDecision rejected(String reason, long retryAfterSeconds) {
            return new RateLimitDecision(false, 0, reason, retryAfterSeconds);
        }
    }
}
