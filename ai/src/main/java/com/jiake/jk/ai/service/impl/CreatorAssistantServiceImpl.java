package com.jiake.jk.ai.service.impl;

import com.jiake.jk.ai.properties.CreatorAssistantProperties;
import com.jiake.jk.ai.service.CreatorAssistantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * A deliberately small AI capability inside the microservice project.
 * It produces advice only. Reads and writes to SW business services are added in later bounded iterations.
 */
@Slf4j
@Service
public class CreatorAssistantServiceImpl implements CreatorAssistantService {

    private static final String SYSTEM_PROMPT = """
            你是 SW 短视频平台的创作者运营助手。你的职责是协助创作者优化标题、简介、标签、选题方向和发布节奏。
            只给出可执行的运营建议，语言简洁，优先提供 2 到 4 个候选方案及理由。
            当前版本没有读取视频处理状态、平台实时数据或用户私有资料的能力：不要声称查询过数据，不要编造审核结果、播放量、违规规则或处理进度。
            你不能替用户发布、删除、修改视频或执行任何写操作。遇到需要真实状态、故障诊断或平台规则的问题，要明确说明将由后续受权限约束的业务工具处理。
            """;

    private final ChatClient creatorAssistantChatClient;
    private final CreatorAssistantProperties properties;

    public CreatorAssistantServiceImpl(@Qualifier("creatorAssistantChatClient") ChatClient creatorAssistantChatClient,
                                       CreatorAssistantProperties properties) {
        this.creatorAssistantChatClient = creatorAssistantChatClient;
        this.properties = properties;
    }

    @Override
    public Flux<StreamEvent> stream(Long userId, String traceId, String message) {
        if (!properties.isEnabled()) {
            return Flux.just(new StreamEvent("error", "创作者助手当前未启用"));
        }
        if (message == null || message.isBlank()) {
            return Flux.just(new StreamEvent("error", "message 不能为空"));
        }
        if (message.length() > properties.getMaxMessageLength()) {
            return Flux.just(new StreamEvent("error", "message 超过长度限制"));
        }
        log.info("Creator assistant stream started, userId={}, traceId={}", userId, traceId);
        return Flux.concat(
                        Flux.just(new StreamEvent("meta", "traceId=" + traceId)),
                        creatorAssistantChatClient.prompt()
                                .system(SYSTEM_PROMPT)
                                .user(message)
                                .stream()
                                .content()
                                .map(token -> new StreamEvent("delta", token))
                                .timeout(Duration.ofSeconds(properties.getStreamTimeoutSeconds())),
                        Flux.just(new StreamEvent("done", "[DONE]"))
                )
                .onErrorResume(TimeoutException.class, exception -> {
                    log.warn("Creator assistant timed out, userId={}, traceId={}", userId, traceId);
                    return Flux.just(new StreamEvent("error", "模型响应超时，请稍后重试"));
                })
                .onErrorResume(exception -> {
                    log.warn("Creator assistant failed, userId={}, traceId={}, type={}",
                            userId, traceId, exception.getClass().getSimpleName());
                    return Flux.just(new StreamEvent("error", "助手暂时不可用，请稍后重试"));
                });
    }
}
