-- Idempotency ledger for reliable comment counter events.
CREATE TABLE IF NOT EXISTS video_comment_event_consumption
(
    comment_id BIGINT UNSIGNED NOT NULL COMMENT 'Consumed comment event id',
    consumed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'First consumption time',
    PRIMARY KEY (comment_id)
) COMMENT 'Comment counter event idempotency ledger';
