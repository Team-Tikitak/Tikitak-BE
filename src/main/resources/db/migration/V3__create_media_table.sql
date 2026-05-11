-- V3__create_media_table.sql
CREATE TABLE media (
    id BIGSERIAL PRIMARY KEY,
    purpose VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size BIGINT NOT NULL,
    url TEXT,
    uploaded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    team_id BIGINT,
    member_id BIGINT NOT NULL
);

CREATE INDEX idx_media_purpose ON media(purpose);
CREATE INDEX idx_media_status ON media(status);
CREATE INDEX idx_media_member_id ON media(member_id);
CREATE INDEX idx_media_team_id ON media(team_id);