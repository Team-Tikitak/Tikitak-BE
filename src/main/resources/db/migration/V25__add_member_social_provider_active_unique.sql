CREATE UNIQUE INDEX uk_member_social_provider_active ON member (social_provider, provider_id) WHERE status = 'ACTIVE';
