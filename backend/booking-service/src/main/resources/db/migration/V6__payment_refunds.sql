CREATE TABLE payment_refunds (
    id BINARY(16) PRIMARY KEY,
    payment_id BINARY(16) NOT NULL,
    provider_refund_id VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    requested_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_payment_refund_payment (payment_id),
    UNIQUE KEY uq_payment_provider_refund (provider_refund_id),
    INDEX ix_payment_refund_status_updated (status, updated_at),
    CONSTRAINT fk_payment_refund_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);
