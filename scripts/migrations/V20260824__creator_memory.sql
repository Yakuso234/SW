CREATE TABLE IF NOT EXISTS creator_memory
(
    id          BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    user_id     BIGINT UNSIGNED NOT NULL,
    type        VARCHAR(24)     NOT NULL,
    content     VARCHAR(500)    NOT NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at  DATETIME        NULL,
    KEY idx_creator_memory_user (user_id, deleted_at, updated_at)
) COMMENT '创作者助手可管理长期偏好';

