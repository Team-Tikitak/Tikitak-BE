ALTER TABLE team_member
    ADD COLUMN profile_media_id BIGINT,
    ADD CONSTRAINT fk_team_member_profile_media
        FOREIGN KEY (profile_media_id) REFERENCES media(id);
