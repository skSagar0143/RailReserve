CREATE TABLE notification_inbox (
    event_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE notification_jobs (
    id BINARY(16) PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'WAITING_FOR_RECIPIENT',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NULL,
    last_error VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_notification_job_event UNIQUE (event_id),
    CONSTRAINT fk_notification_job_event FOREIGN KEY (event_id) REFERENCES notification_inbox(event_id),
    INDEX ix_notification_job_dispatch (status, next_attempt_at)
);
