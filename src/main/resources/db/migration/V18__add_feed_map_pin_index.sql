CREATE INDEX idx_feed_map_pin_lookup
    ON feed(team_id, place_id)
    WHERE deleted_at IS NULL AND place_id IS NOT NULL;
