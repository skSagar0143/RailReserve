ALTER TABLE seat_inventory
    ADD COLUMN fare_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN held_until TIMESTAMP(6) NULL;

ALTER TABLE bookings
    ADD COLUMN request_hash CHAR(64) NOT NULL DEFAULT '';

ALTER TABLE booking_seats
    DROP INDEX uq_booking_inventory,
    ADD INDEX ix_booking_seats_inventory (inventory_id);

ALTER TABLE outbox_events
    ADD INDEX ix_outbox_unpublished (published_at, created_at);
