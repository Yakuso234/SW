-- Run once. This migration creates the cursor column and its covering index.
ALTER TABLE video
    ADD COLUMN published_at datetime NULL COMMENT 'published timestamp' AFTER updated_at;

UPDATE video
SET published_at = COALESCE(updated_at, created_at)
WHERE status = 5
  AND published_at IS NULL;

CREATE INDEX idx_video_status_published_at_id
    ON video (status, published_at DESC, id DESC);
