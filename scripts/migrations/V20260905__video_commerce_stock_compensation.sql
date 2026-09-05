CREATE TABLE IF NOT EXISTS video_commerce_stock_compensation
(
    id              BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    order_id        BIGINT UNSIGNED NOT NULL,
    flash_sale_id   BIGINT UNSIGNED NOT NULL,
    status          TINYINT         NOT NULL DEFAULT 0,
    retry_count     INT             NOT NULL DEFAULT 0,
    next_retry_at   DATETIME        NULL,
    lease_expire_at DATETIME        NULL,
    completed_at    DATETIME        NULL,
    last_error      VARCHAR(255)    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_stock_compensation_order (order_id),
    KEY idx_stock_compensation_due (status, next_retry_at, lease_expire_at)
) COMMENT '视频秒杀库存回补任务';
