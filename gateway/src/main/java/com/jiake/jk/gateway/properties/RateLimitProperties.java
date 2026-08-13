package com.jiake.jk.gateway.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Gateway write-path rate limit policy. All limits use the same sliding window. */
@Data
@Component
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private int windowSeconds = 60;
    private int ipLimit = 120;
    private int userLimit = 60;
    private int routeLimit = 300;
    private List<String> protectedMethods = List.of("POST", "PUT", "PATCH", "DELETE");
    private List<String> protectedPathPrefixes = List.of("/ai/");
    private Map<String, Integer> routeLimits = Map.of();
}
