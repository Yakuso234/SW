package com.jiake.jk.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Arrays;

@Configuration
public class ChatClientConfig {

    @Bean
    @ConditionalOnProperty(prefix = "sw.ai.legacy", name = "enabled", havingValue = "true")
    public ChatClient chatClient(
            ChatClient.Builder chatClientBuilder,
            ToolCallbackProvider toolCallbackProvider) {
        ToolCallback[] toolCallbacks = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .filter(tool -> tool.getToolDefinition().name().equals("archival_memory_search"))
                .toArray(ToolCallback[]::new);
        return chatClientBuilder
                .defaultToolCallbacks(toolCallbacks)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "sw.ai.legacy", name = "enabled", havingValue = "true")
    public ChatClient titleClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "sw.ai.legacy", name = "enabled", havingValue = "true")
    public ChatClient memoryClient(
            ChatClient.Builder chatClientBuilder,
            ToolCallbackProvider toolCallbackProvider) {
        ToolCallback[] toolCallbacks = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .filter(tool -> tool.getToolDefinition().name().equals("archival_memory_insert"))
                .toArray(ToolCallback[]::new);
        return chatClientBuilder
                .defaultToolCallbacks(toolCallbacks)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    /** The new SW creator assistant intentionally starts without legacy memory tools. */
    @Bean
    public ChatClient creatorAssistantChatClient(ChatClient.Builder chatClientBuilder) {
        return chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
