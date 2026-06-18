ALTER TABLE member_device_token
    ADD CONSTRAINT chk_member_device_token_platform CHECK (platform IN ('ANDROID', 'IOS'));
