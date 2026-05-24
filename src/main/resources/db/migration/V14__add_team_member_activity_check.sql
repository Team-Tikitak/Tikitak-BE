ALTER TABLE team_member
    ADD COLUMN last_activity_checked_at TIMESTAMP;

UPDATE team_member
SET last_activity_checked_at = created_at
WHERE last_activity_checked_at IS NULL;

ALTER TABLE team_member
    ALTER COLUMN last_activity_checked_at SET NOT NULL;

CREATE INDEX idx_feed_team_created_author_active
    ON feed(team_id, created_at, team_member_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_team_member_member_status_team
    ON team_member(member_id, status, team_id);
