CREATE TABLE media_upload (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    purpose VARCHAR(50) NOT NULL CHECK (purpose IN ('FEED_IMAGE', 'DAILY_QUESTION_IMAGE', 'TEAM_IMAGE', 'PROFILE_IMAGE')),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'EXPIRED')),
    team_id BIGINT REFERENCES team(id) ON DELETE RESTRICT,
    member_id BIGINT NOT NULL REFERENCES member(id) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    expires_at TIMESTAMP
);

CREATE INDEX idx_media_upload_public_id ON media_upload(public_id);
CREATE INDEX idx_media_upload_member_id ON media_upload(member_id);
CREATE INDEX idx_media_upload_status ON media_upload(status);

ALTER TABLE media
    ADD COLUMN upload_id BIGINT REFERENCES media_upload(id) ON DELETE RESTRICT;

ALTER TABLE media DROP CONSTRAINT IF EXISTS media_status_check;
ALTER TABLE media
    ADD CONSTRAINT media_status_check CHECK (status IN ('PENDING', 'UPLOADED', 'USED', 'DELETED'));

CREATE INDEX idx_media_upload_id ON media(upload_id);
CREATE INDEX idx_media_status_created_at ON media(status, created_at);
CREATE INDEX idx_media_status_uploaded_at ON media(status, uploaded_at);
