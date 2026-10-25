CREATE TABLE payments (
    id BINARY(16) PRIMARY KEY,
    booking_id BINARY(16) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_link_id VARCHAR(128) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(24) NOT NULL,
    payment_url VARCHAR(2048) NOT NULL,
    provider_payment_id VARCHAR(128) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    paid_at TIMESTAMP(6) NULL,
    UNIQUE KEY uq_payment_booking (booking_id),
    UNIQUE KEY uq_payment_provider_link (provider, provider_link_id),
    UNIQUE KEY uq_payment_provider_payment (provider, provider_payment_id),
    CONSTRAINT fk_payment_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
    INDEX ix_payment_status_created (status, created_at)
);