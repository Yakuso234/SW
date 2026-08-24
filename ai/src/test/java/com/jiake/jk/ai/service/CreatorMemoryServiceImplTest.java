package com.jiake.jk.ai.service;

import com.jiake.jk.ai.dto.SaveCreatorMemoryRequest;
import com.jiake.jk.ai.entity.CreatorMemory;
import com.jiake.jk.ai.properties.CreatorAssistantProperties;
import com.jiake.jk.ai.repository.CreatorMemoryRepository;
import com.jiake.jk.ai.service.impl.CreatorMemoryServiceImpl;
import com.jiake.jk.common.exception.YHClientException;
import com.jiake.jk.common.utils.SnowflakeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorMemoryServiceImplTest {
    @Mock private CreatorMemoryRepository repository;
    @Mock private CreatorMemoryVectorStore vectorStore;
    @Mock private ReactiveStringRedisTemplate redisTemplate;
    @Mock private SnowflakeUtils snowflakeUtils;

    private CreatorMemoryServiceImpl service;

    @BeforeEach
    void setUp() {
        CreatorAssistantProperties properties = new CreatorAssistantProperties();
        properties.setMemoryEnabled(true);
        service = new CreatorMemoryServiceImpl(repository, vectorStore, redisTemplate, snowflakeUtils, properties);
    }

    @Test
    void save_shouldKeepMysqlFactWhenVectorIndexingFails() {
        when(snowflakeUtils.nextId()).thenReturn(101L);
        when(repository.insertMemory(eq(101L), eq(7L), eq("STYLE"), eq("偏好短标题"), any(), any()))
                .thenReturn(Mono.just(1));
        doThrow(new IllegalStateException("milvus unavailable")).when(vectorStore).upsert(101L, 7L, "偏好短标题");

        StepVerifier.create(service.save(7L, new SaveCreatorMemoryRequest("STYLE", "偏好短标题")))
                .expectNextMatches(memory -> memory.id() == 101L && "STYLE".equals(memory.type())
                        && "偏好短标题".equals(memory.content()))
                .verifyComplete();
    }

    @Test
    void buildContext_shouldBypassMemoryForOperationalQuestion() {
        StepVerifier.create(service.buildContext(7L, "videoId 100 的转码失败原因是什么"))
                .expectNext("")
                .verifyComplete();

        verifyNoInteractions(repository, vectorStore, redisTemplate);
    }

    @Test
    void delete_shouldRejectMemoryThatDoesNotBelongToCurrentUser() {
        when(repository.softDelete(eq(101L), eq(7L), any())).thenReturn(Mono.just(0));

        StepVerifier.create(service.delete(7L, 101L))
                .expectErrorMatches(error -> error instanceof YHClientException
                        && "记忆不存在或已删除".equals(error.getMessage()))
                .verify();

        verifyNoInteractions(vectorStore);
    }
}
