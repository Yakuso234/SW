-- Run once. Recipient inbox for fanout-on-write follow feeds.
CREATE TABLE video_feed_inbox (
    id bigint unsigned NOT NULL,
    recipient_id bigint unsigned NOT NULL,
    video_id bigint unsigned NOT NULL,
    creator_id bigint unsigned NOT NULL,
    published_at datetime NOT NULL,
    created_at datetime NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_video_feed_inbox_recipient_video (recipient_id, video_id),
    KEY idx_video_feed_inbox_recipient_published_id (recipient_id, published_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
