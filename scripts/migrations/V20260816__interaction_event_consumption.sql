CREATE TABLE IF NOT EXISTS video_interaction_event_consumption
(
    event_id    BIGINT UNSIGNED NOT NULL COMMENT '已消费的互动事件ID' PRIMARY KEY,
    consumed_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次消费时间'
) COMMENT '点赞收藏事件幂等表';
