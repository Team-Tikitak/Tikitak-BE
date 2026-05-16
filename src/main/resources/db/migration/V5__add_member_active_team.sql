ALTER TABLE member
    ADD COLUMN active_team_id BIGINT NULL;

ALTER TABLE member
    ADD CONSTRAINT fk_member_active_team
        FOREIGN KEY (active_team_id)
        REFERENCES team (id)
        ON DELETE SET NULL;
