package com.jiake.jk.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiake.jk.video.pojo.entity.VideoProcessingRecoveryRequest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VideoProcessingRecoveryRequestMapper extends BaseMapper<VideoProcessingRecoveryRequest> {

    @Insert("""
            INSERT IGNORE INTO video_processing_recovery_request
            (id, idempotency_key, video_id, requested_by, trace_id, status, reason, outbox_id, created_at, updated_at)
            VALUES (#{id}, #{idempotencyKey}, #{videoId}, #{requestedBy}, #{traceId},
                    'PENDING', NULL, NULL, #{createdAt}, #{updatedAt})
            """)
    int insertIgnore(VideoProcessingRecoveryRequest request);

    @Select("""
            SELECT id, idempotency_key, video_id, requested_by, trace_id, status, reason,
                   outbox_id, created_at, updated_at
            FROM video_processing_recovery_request
            WHERE idempotency_key = #{idempotencyKey}
            LIMIT 1
            """)
    VideoProcessingRecoveryRequest selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT id, idempotency_key, video_id, requested_by, trace_id, status, reason,
                   outbox_id, created_at, updated_at
            FROM video_processing_recovery_request
            WHERE idempotency_key = #{idempotencyKey}
            LIMIT 1
            FOR UPDATE
            """)
    VideoProcessingRecoveryRequest selectByIdempotencyKeyForUpdate(
            @Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE video_processing_recovery_request
            SET status = 'ACCEPTED', reason = NULL, outbox_id = #{outboxId}, updated_at = #{updatedAt}
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int markAccepted(@Param("id") Long id, @Param("outboxId") Long outboxId,
                     @Param("updatedAt") java.time.LocalDateTime updatedAt);

    @Update("""
            UPDATE video_processing_recovery_request
            SET status = 'REJECTED', reason = 'PRECONDITION_NOT_MET', outbox_id = NULL,
                updated_at = #{updatedAt}
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int markPreconditionRejected(@Param("id") Long id,
                                 @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
