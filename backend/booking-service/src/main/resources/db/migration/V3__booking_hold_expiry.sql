ALTER TABLE bookings ADD COLUMN hold_expires_at TIMESTAMP(6) NULL;
CREATE INDEX ix_booking_hold_expiry ON bookings(status, hold_expires_at);
