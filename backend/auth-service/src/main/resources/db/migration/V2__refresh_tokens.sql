CREATE TABLE auth_refresh_tokens (
    id BINARY(16) PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_auth_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_refresh_token_user FOREIGN KEY (user_id) REFERENCES auth_users(id) ON DELETE CASCADE,
    INDEX ix_refresh_token_user (user_id),
    INDEX ix_refresh_token_expiry (expires_at)
);
