package com.jiake.jk.ai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Runtime guardrails for the SW creator assistant, independent from legacy chat sessions. */
@Data
@Component
@ConfigurationProperties(prefix = "sw.ai.creator-assistant")
public class CreatorAssistantProperties {
    private boolean enabled = true;
    private int streamTimeoutSeconds = 45;
    private int maxMessageLength = 1500;
}
