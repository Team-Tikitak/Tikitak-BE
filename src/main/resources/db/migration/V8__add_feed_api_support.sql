ALTER TABLE place
    ADD COLUMN external_place_id VARCHAR(100);

CREATE UNIQUE INDEX ux_place_external_place_id ON place(external_place_id);

ALTER TABLE feed_image
    ADD COLUMN media_id BIGINT REFERENCES media(id) ON DELETE RESTRICT;

CREATE INDEX idx_feed_team_deleted_created_id ON feed(team_id, deleted_at, created_at, id);
CREATE INDEX idx_feed_image_feed_order ON feed_image(feed_id, order_index);
CREATE INDEX idx_feed_image_media_id ON feed_image(media_id);

ALTER TABLE media
    ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_media_status_deleted_at ON media(status, deleted_at);

ALTER TABLE feed_reaction
    ADD COLUMN reaction_type VARCHAR(50);

UPDATE feed_reaction
SET reaction_type = 'TAK_LEADER'
WHERE reaction_type IS NULL;

ALTER TABLE feed_reaction
    ALTER COLUMN reaction_type SET NOT NULL;
