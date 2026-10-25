ALTER TABLE auth_refresh_tokens ADD COLUMN family_id BINARY(16) NULL;
UPDATE auth_refresh_tokens SET family_id = id WHERE family_id IS NULL;
ALTER TABLE auth_refresh_tokens MODIFY COLUMN family_id BINARY(16) NOT NULL;
CREATE INDEX ix_refresh_token_family ON auth_refresh_tokens(family_id, revoked_at);
