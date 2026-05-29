ALTER TABLE member
    ADD COLUMN profile_character_type VARCHAR(50),
    ADD COLUMN onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE member
    ADD CONSTRAINT ck_member_profile_character_type
        CHECK (
            profile_character_type IS NULL
            OR profile_character_type IN (
                'TAK_LEADER',
                'TAK_SPARK',
                'TAK_BURNER',
                'TAK_BUILDER',
                'TAK_FREE',
                'TAK_CARE'
            )
        );

ALTER TABLE member
    ADD CONSTRAINT ck_member_onboarding_profile_character
        CHECK (
            onboarding_completed = FALSE
            OR profile_character_type IS NOT NULL
        );
