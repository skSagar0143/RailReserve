CREATE TABLE train_admin_audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor VARCHAR(128) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    details TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX ix_train_admin_audit_target_created (target_type, target_id, created_at),
    INDEX ix_train_admin_audit_actor_created (actor, created_at)
);
