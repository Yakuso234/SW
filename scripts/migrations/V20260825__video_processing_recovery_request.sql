CREATE TABLE IF NOT EXISTS video_processing_recovery_request
(
    id               BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    idempotency_key  VARCHAR(255)    NOT NULL,
    video_id         BIGINT UNSIGNED NOT NULL,
    requested_by     VARCHAR(64)     NOT NULL,
    trace_id         VARCHAR(128)    NOT NULL,
    status           VARCHAR(16)     NOT NULL,
    reason           VARCHAR(64)     NULL,
    outbox_id        BIGINT UNSIGNED NULL,
    created_at       DATETIME        NOT NULL,
    updated_at       DATETIME        NOT NULL,
    UNIQUE KEY uk_video_recovery_idempotency (idempotency_key),
    KEY idx_video_recovery_video (video_id, created_at)
) COMMENT '视频处理恢复请求幂等回执';
