package com.jiake.jk.ai.service.impl;

import com.jiake.jk.ai.properties.CreatorAssistantProperties;
import com.jiake.jk.ai.service.CreatorAssistantService;
import com.jiake.jk.ai.service.CreatorMemoryService;
import com.jiake.jk.ai.tool.VideoProcessingTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * A bounded AI capability inside SW: creation advice plus permission-scoped read tools.
 * The service does not own video data and cannot execute write commands.
 */
@Slf4j
@Service
public class CreatorAssistantServiceImpl implements CreatorAssistantService {

    private static final String SYSTEM_PROMPT = """
            你是 SW 短视频平台的创作者运营助手，协助创作者优化标题、简介、标签、选题方向和发布节奏。
            给出简洁、可执行的建议，优先提供 2 到 4 个候选方案及理由。
            当用户提供明确 videoId 并询问上传、转码、审核或处理进度时，必须调用 query_video_processing_status 获取真实事实；当用户询问明确 videoId 的失败原因、转码失败或下一步处理建议时，只能调用 diagnose_video_processing_failure，不能仅调用 query_video_processing_status 后自行分析。没有 videoId 时先请用户提供。工具结果是唯一的状态事实来源，禁止猜测或编造。
            对工具字段只能按其字面含义转述：retryCount 仅表示系统累计计数，不是失败、重试原因或恢复过程的证据。即使 retryCount 大于 0，也必须使用“系统计数；未提供原因”的固定表述；严禁出现“曾有异常”“短暂重试”“自动恢复”“最终成功”等推断性措辞。输出 updatedAt 时只能使用固定格式“工具返回最近更新时间：<字段值>”，不得追加解释、否定说明或推断其是否真实、是否为占位、是否为发布时间或当前时间。除非工具明确提供原因，否则不得推断审核结论或任何未返回的过程事实。
            失败诊断工具只会返回服务端失败摘要匹配出的类别和建议；必须按其结果转述，未匹配时明确说明原因未知。你不能替用户发布、删除、修改视频或执行任何写操作。播放量和违规规则当前仍没有工具，不能编造。
            """;

    private final ChatClient creatorAssistantChatClient;
    private final CreatorAssistantProperties properties;
    private final VideoProcessingTools videoProcessingTools;
    private final CreatorMemoryService creatorMemoryService;

    public CreatorAssistantServiceImpl(@Qualifier("creatorAssistantChatClient") ChatClient creatorAssistantChatClient,
                                       CreatorAssistantProperties properties,
                                       VideoProcessingTools videoProcessingTools,
                                       CreatorMemoryService creatorMemoryService) {
        this.creatorAssistantChatClient = creatorAssistantChatClient;
        this.properties = properties;
        this.videoProcessingTools = videoProcessingTools;
        this.creatorMemoryService = creatorMemoryService;
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
        StringBuilder assistantText = new StringBuilder();
        return creatorMemoryService.buildContext(userId, message).flatMapMany(memoryContext -> Flux.concat(
                        Flux.just(new StreamEvent("meta", "traceId=" + traceId)),
                        creatorAssistantChatClient.prompt()
                                .system(SYSTEM_PROMPT + memoryContext)
                                .user(message)
                                .tools(videoProcessingTools)
                                .toolContext(Map.of("creatorUserId", userId, "traceId", traceId))
                                .stream()
                                .content()
                                .map(token -> {
                                    assistantText.append(token);
                                    return new StreamEvent("delta", token);
                                })
                                .timeout(Duration.ofSeconds(properties.getStreamTimeoutSeconds())),
                        Flux.just(new StreamEvent("done", "[DONE]")),
                        Flux.defer(() -> creatorMemoryService.appendSessionTurn(userId, message, assistantText.toString())
                                .onErrorResume(error -> {
                                    log.debug("Creator session memory write skipped, type={}",
                                            error.getClass().getSimpleName());
                                    return reactor.core.publisher.Mono.empty();
                                })
                                .thenMany(Flux.empty()))
                ))
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
