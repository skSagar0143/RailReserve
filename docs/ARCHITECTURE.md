# Architecture and service boundaries

Auth service owns identities and roles. Train service owns stations, trains, route stops and schedules. Booking service owns seat inventory, reservations, cancellations, idempotency and an outbox. Notification service consumes notification events. Each service owns its database; services must not directly access another service's tables.

## Booking consistency

1. Require authentication and an idempotency key.
2. Validate fares and route data from authoritative APIs.
3. In one database transaction, lock inventory rows or use conditional atomic updates, verify all seats, and persist the booking and outbox event together.
4. Publish committed outbox events to Kafka. Publishing may repeat after a crash; consumers must deduplicate by event ID.
5. Cancellation must be authorized and release inventory exactly once.

Redis is a cache, not the booking source of truth. Do not claim exactly-once external SMS/email effects; use at-least-once delivery, idempotent consumers and provider idempotency when supported.
