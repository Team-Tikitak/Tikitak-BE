-- V3__create_media_table.sql
CREATE TABLE media (
    id BIGSERIAL PRIMARY KEY,
    purpose VARCHAR(50) NOT NULL CHECK (purpose IN ('FEED_IMAGE', 'DAILY_QUESTION_IMAGE', 'TEAM_IMAGE', 'PROFILE_IMAGE')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'UPLOADED', 'DELETED')),
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size BIGINT NOT NULL,
    url TEXT,
    key VARCHAR(255),
    uploaded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    team_id BIGINT REFERENCES team(id) ON DELETE RESTRICT,
    member_id BIGINT NOT NULL REFERENCES member(id) ON DELETE RESTRICT
);

CREATE INDEX idx_media_purpose ON media(purpose);
CREATE INDEX idx_media_status ON media(status);
CREATE INDEX idx_media_member_id ON media(member_id);
CREATE INDEX idx_media_team_id ON media(team_id);