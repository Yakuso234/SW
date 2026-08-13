package com.jiake.jk.ai.service.impl;

import com.jiake.jk.ai.service.CustomerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@ConditionalOnProperty(prefix = "sw.ai.legacy", name = "enabled", havingValue = "true")
public class CustomerServiceImpl extends AbstractConversationChatService implements CustomerService {

    private static final String SYSTEM_PROMPT = "你是一个商城智能客服。";

    @Override
    public Flux<Object> chat(Long userId, Long conversationId, String msg, boolean enableThinking) {
        return getHistoryMessages(userId, conversationId)
                .flatMapMany(historyMessages -> {
                    String prompt = buildPrompt(SYSTEM_PROMPT, msg, historyMessages);
                    return streamChatResponse(userId, conversationId, msg, prompt, enableThinking);
                });
    }
}
