package com.jiake.jk.ai.repository;

import com.jiake.jk.ai.entity.CreatorMemory;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface CreatorMemoryRepository extends ReactiveCrudRepository<CreatorMemory, Long> {
    @Modifying
    @Query("INSERT INTO creator_memory(id, user_id, type, content, created_at, updated_at) " +
            "VALUES (:id, :userId, :type, :content, :createdAt, :updatedAt)")
    Mono<Integer> insertMemory(Long id, Long userId, String type, String content,
                               LocalDateTime createdAt, LocalDateTime updatedAt);

    @Query("SELECT * FROM creator_memory WHERE user_id = :userId AND deleted_at IS NULL ORDER BY updated_at DESC")
    Flux<CreatorMemory> findActiveByUserId(Long userId);

    @Query("SELECT * FROM creator_memory WHERE id = :id AND user_id = :userId AND deleted_at IS NULL LIMIT 1")
    Mono<CreatorMemory> findActiveByIdAndUserId(Long id, Long userId);

    @Query("UPDATE creator_memory SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :id AND user_id = :userId AND deleted_at IS NULL")
    Mono<Integer> softDelete(Long id, Long userId, LocalDateTime deletedAt);
}
