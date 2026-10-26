CREATE TABLE payment_webhook_events (
    event_id VARCHAR(128) PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
