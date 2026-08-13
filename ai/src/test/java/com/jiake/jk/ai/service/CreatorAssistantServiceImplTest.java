package com.jiake.jk.ai.service;

import com.jiake.jk.ai.properties.CreatorAssistantProperties;
import com.jiake.jk.ai.service.impl.CreatorAssistantServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;

class CreatorAssistantServiceImplTest {

    @Test
    void stream_shouldRejectBlankMessageBeforeCallingModel() {
        CreatorAssistantServiceImpl service = new CreatorAssistantServiceImpl(mock(ChatClient.class), properties(true));

        StepVerifier.create(service.stream(900001L, "trace-creator-001", " "))
                .expectNextMatches(event -> "error".equals(event.event()) && event.data().contains("不能为空"))
                .verifyComplete();
    }

    @Test
    void stream_shouldRejectWhenFeatureIsDisabled() {
        CreatorAssistantServiceImpl service = new CreatorAssistantServiceImpl(mock(ChatClient.class), properties(false));

        StepVerifier.create(service.stream(900001L, "trace-creator-002", "给我三个标题"))
                .expectNextMatches(event -> "error".equals(event.event()) && event.data().contains("未启用"))
                .verifyComplete();
    }

    private CreatorAssistantProperties properties(boolean enabled) {
        CreatorAssistantProperties properties = new CreatorAssistantProperties();
        properties.setEnabled(enabled);
        return properties;
    }
}
