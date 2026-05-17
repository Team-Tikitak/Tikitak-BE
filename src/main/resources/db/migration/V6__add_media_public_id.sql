CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE media
    ADD COLUMN public_id UUID;

UPDATE media
SET public_id = gen_random_uuid()
WHERE public_id IS NULL;

ALTER TABLE media
    ALTER COLUMN public_id SET NOT NULL;

CREATE UNIQUE INDEX ux_media_public_id ON media(public_id);
