-- flyway:executeInTransaction=false
CREATE INDEX CONCURRENTLY idx_media_upload_expires_at ON media_upload (expires_at);
