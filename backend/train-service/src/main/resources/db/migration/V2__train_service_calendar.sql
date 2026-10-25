CREATE TABLE train_service_calendar (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    train_id BIGINT NOT NULL,
    service_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_service_calendar_train FOREIGN KEY (train_id) REFERENCES trains(id),
    CONSTRAINT uq_train_service_date UNIQUE (train_id, service_date),
    INDEX ix_service_calendar_date_status (service_date, status, train_id)
);
