# Architecture and service boundaries

Auth service owns identities and roles. Train service owns stations, trains, route stops and scheduled service dates. Booking service owns seat inventory, server-configured fares, reservations, unpaid holds, cancellations, idempotency and a transactional outbox. Notification service consumes booking events into a durable, deduplicated inbox and notification-job table. Each service owns its database; services must not directly access another service's tables.

## Booking consistency

1. Require an authenticated customer and an idempotency key.
2. Use the booking database's seat inventory and configured fares; never accept a client-supplied total. Admins must keep train schedule and booking inventory records consistent because cross-service schedule validation is not yet implemented.
3. In one database transaction, lock the requested inventory rows, verify every seat belongs to the requested train/date and is available, calculate the total from server-side fares, create the pending-payment booking, hold seats for 10 minutes and write an outbox event.
4. A scheduled expiry job releases unpaid holds and emits an expiry event. Cancellation is allowed only for pending-payment bookings.
5. Publish committed outbox events to Kafka. Publishing may repeat after a crash; consumers deduplicate by event ID. Notification jobs are durable but real email/SMS delivery is not configured yet.

Redis is used for short-lived train-search caching (30-second TTL with a version key invalidated after admin changes) and distributed authentication rate limits. Redis is never the booking source of truth. Do not claim exactly-once external SMS/email effects; use at-least-once delivery, idempotent consumers and provider idempotency when supported.
