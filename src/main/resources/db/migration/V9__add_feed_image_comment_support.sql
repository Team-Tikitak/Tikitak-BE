ALTER TABLE feed_comment
    ADD COLUMN feed_image_id BIGINT REFERENCES feed_image(id) ON DELETE CASCADE;

ALTER TABLE feed_comment
    ALTER COLUMN feed_image_id SET NOT NULL;

ALTER TABLE feed_comment
    ALTER COLUMN position_x TYPE NUMERIC(8, 6) USING position_x::numeric,
    ALTER COLUMN position_y TYPE NUMERIC(8, 6) USING position_y::numeric;

ALTER TABLE feed_comment
    ADD CONSTRAINT chk_feed_comment_position_x_range CHECK (position_x >= 0 AND position_x <= 1),
    ADD CONSTRAINT chk_feed_comment_position_y_range CHECK (position_y >= 0 AND position_y <= 1);

CREATE INDEX idx_feed_comment_feed_created_id
    ON feed_comment(feed_id, created_at, id);

CREATE INDEX idx_feed_comment_feed_image_created_id
    ON feed_comment(feed_image_id, created_at, id);
