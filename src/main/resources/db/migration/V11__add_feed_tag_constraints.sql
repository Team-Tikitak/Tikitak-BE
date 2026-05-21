ALTER TABLE feed_tag
    ADD CONSTRAINT uk_feed_tag_feed_member UNIQUE (feed_id, team_member_id);

CREATE INDEX idx_feed_tag_team_member_feed
    ON feed_tag (team_member_id, feed_id);

CREATE INDEX idx_feed_active_team_created_id
    ON feed (team_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_feed_comment_active_feed
    ON feed_comment (feed_id)
    WHERE is_deleted = false;

CREATE INDEX idx_feed_reaction_feed_type
    ON feed_reaction (feed_id, reaction_type);
