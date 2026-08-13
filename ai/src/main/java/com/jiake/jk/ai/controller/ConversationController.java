package com.jiake.jk.ai.controller;

import com.jiake.jk.ai.response.ConversationMsgResponse;
import com.jiake.jk.ai.response.ConversationResponse;
import com.jiake.jk.ai.service.ConversationService;
import com.jiake.jk.ai.utils.ReactiveUserContext;
import com.jiake.jk.common.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@Tag(name = "Conversation")
@RestController
@ConditionalOnProperty(prefix = "sw.ai.legacy", name = "enabled", havingValue = "true")
@RequestMapping("/me/conversation")
public class ConversationController {

    @Autowired
    private ConversationService conversationService;

    @Operation(summary = "创建会话")
    @PostMapping
    Mono<Result<String>> postConversation() {
        return ReactiveUserContext.getUserId().flatMap(id -> conversationService.postConversation(id)
                .map(Result::success));
    }

    @Operation(summary = "获取会话列表")
    @GetMapping("/list")
    Mono<Result<List<ConversationResponse>>> getConversations() {
        return ReactiveUserContext.getUserId().flatMap(id -> conversationService.getConversations(id)
                .map(Result::success));
    }

    @Operation(summary = "获取会话的信息")
    @GetMapping("/msg/{conversationId}")
    Mono<Result<List<ConversationMsgResponse>>> getConversationMsg(@PathVariable Long conversationId) {
        return ReactiveUserContext.getUserId().flatMap(id -> conversationService.getConversationMsg(id, conversationId)
                .map(Result::success));
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/{conversationId}")
    Mono<Result<String>> deleteConversation(@PathVariable Long conversationId) {
        return ReactiveUserContext.getUserId().flatMap(id -> conversationService.deleteConversation(id, conversationId)
                .map(Result::success));
    }

    @Operation(summary = "生成会话标题")
    @PostMapping("/title/{conversationId}")
    Mono<Result<String>> generateConversationTitle(@PathVariable Long conversationId) {
        return ReactiveUserContext.getUserId().flatMap(id -> conversationService.generateConversationTitle(id, conversationId)
                .map(Result::success));
    }

}
