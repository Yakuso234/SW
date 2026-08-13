package com.jiake.jk.ai.pool;

import com.jiake.jk.ai.service.MemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.concurrent.ThreadPoolExecutor;

@Component
@ConditionalOnProperty(prefix = "sw.ai.legacy", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MemoryThreadPool {
    private final MemoryService memoryService;

    private final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
            2, // core pool size
            10, // maximum pool size
            60L, // keep-alive time
            java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(100)
    );

    public void submit(Long userId, String msg) {
        threadPoolExecutor.submit(() -> memoryService.extractAndStoreMemory(userId, msg));
    }
}
