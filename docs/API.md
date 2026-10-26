# API reference (current implementation)

Base URL through the local gateway: `http://localhost:8080`. In production, use HTTPS and do not expose service ports directly. Dates use ISO format `YYYY-MM-DD`; times in schedule responses are local train-service times.

## Authentication

### Register a customer
`POST /api/v1/auth/register`

```json
{"email":"traveller@example.com","password":"A-long-password-123","displayName":"Traveller"}
```

Password must be 12–72 characters. Public registration always creates the CUSTOMER role.

### Login
`POST /api/v1/auth/login`

```json
{"email":"traveller@example.com","password":"A-long-password-123"}
```

Returns an RS256 access token (default 15-minute lifetime), a rotating refresh token and role. Keep tokens in memory on clients; never put access or refresh tokens in localStorage or logs.

### Refresh
`POST /api/v1/auth/refresh`

```json
{"refreshToken":"<refresh-token>"}
```

Each refresh token is one-time-use. Reuse of a rotated token revokes its token family. Sign in again after family revocation.

### Logout
`POST /api/v1/auth/logout`

```json
{"refreshToken":"<refresh-token>"}
```

### Current account
`GET /api/v1/auth/me`

Requires `Authorization: Bearer <access-token>`.

Login is limited per source IP; the local Nginx gateway also applies general per-IP rate limits.

## Train search

### Search stations
`GET /api/v1/stations?q=NDL`

### Search scheduled trains
`GET /api/v1/trains/search?from=NDLS&to=BCT&date=2026-11-02`

Only active trains, active stations and SCHEDULED calendar rows are returned. An empty list means no configured service matched; this repository does not ship a verified railway timetable.

## Administrator setup

All admin endpoints require a JWT with the ADMIN role. Public registration cannot assign ADMIN; see [ADMIN_BOOTSTRAP.md](ADMIN_BOOTSTRAP.md).

- `POST /api/v1/admin/stations`: `{"code":"NDLS","name":"New Delhi","city":"Delhi"}`
- `POST /api/v1/admin/trains`: `{"trainNumber":"12951","name":"Mumbai Rajdhani Express"}`
- `POST /api/v1/admin/trains/{trainId}/stops`: `{"stationId":1,"stopSequence":1,"arrivalTime":null,"departureTime":"16:25:00","dayOffset":0}`
- `POST /api/v1/admin/trains/{trainId}/service-dates`: `{"serviceDate":"2026-11-02"}`
- `POST /api/v1/admin/inventory`: `{"trainId":1,"journeyDate":"2026-11-02","coachCode":"A1","seatNumber":"01","fareAmount":2145.00}`

A train's service date and its booking-service seat inventory must be configured consistently. Seat fares are server-side values; clients cannot submit a total price.

## Booking

All booking endpoints require an access token. The customer ID comes from the JWT subject, never from request JSON.

### Seat availability
`GET /api/v1/bookings/availability?trainId=1&journeyDate=2026-11-02`

### Create a 10-minute seat hold
`POST /api/v1/bookings`

Headers: `Authorization: Bearer <access-token>`, `Idempotency-Key: <unique-value-at-least-16-characters>`

```json
{"trainId":1,"journeyDate":"2026-11-02","seatIds":[12,13]}
```

The server locks inventory rows, verifies all seats belong to the requested train/date, computes the total from configured seat fares, marks seats HELD, stores the booking and outbox event in one database transaction. The booking remains PENDING_PAYMENT until the signed payment-provider webhook confirms payment. Unpaid holds expire automatically.

### Booking history/details/cancellation
- `GET /api/v1/bookings/mine`
- `GET /api/v1/bookings/{bookingId}`
- `POST /api/v1/bookings/{bookingId}/cancel` — only pending-payment holds can be cancelled through this endpoint.

## Payments (Razorpay hosted payment links)

Payment checkout is disabled by default. Set `PAYMENT_ENABLED=true`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, and `RAZORPAY_WEBHOOK_SECRET` using environment/secret management before testing. Use Razorpay test-mode credentials first.

- `POST /api/v1/payments/bookings/{bookingId}/checkout` — authenticated booking owner only; creates or returns the existing checkout link for a valid, unexpired PENDING_PAYMENT booking.
- `GET /api/v1/payments/bookings/{bookingId}` — authenticated booking owner only; returns booking/payment status.
- `POST /api/v1/payments/webhook` — provider callback; requires the raw request body and `X-Razorpay-Signature`. When supplied, `X-Razorpay-Event-Id` is persisted to deduplicate provider redelivery. Configure Razorpay's `payment_link.paid` webhook to this endpoint.
- `GET /api/v1/admin/payments/reconciliation?limit=50` — ADMIN only; lists amount/currency mismatches, payments arriving after the seat hold expires, and refund requests requiring reconciliation. The limit is clamped to 1–100.
- `POST /api/v1/admin/payments/{paymentId}/refund` — ADMIN only; request body: `{"reason":"Seat hold expired before payment confirmation"}`. Requests a **full refund** through Razorpay only for captured payments flagged as paid-after-expiry or amount/currency mismatch, and records the refund intent before contacting the provider. Confirmed bookings must go through a cancellation workflow before a refund can be requested. The payment can have only one refund request in this implementation. A repeated request returns the stored result and is not submitted again.

A browser redirect is not proof of payment. Only a signature-verified webhook with the expected amount and currency confirms the booking and writes a BookingConfirmed outbox event. Refund outcomes reported as processed or pending are recorded. If the provider call fails or returns an ambiguous outcome, the payment is moved to `REFUND_REVIEW_REQUIRED`; operators must check Razorpay before taking any further action. The application deliberately does not automatically resubmit uncertain refund calls, to avoid duplicate refunds. If Razorpay does not send the event ID header, provider-event-level deduplication is unavailable. Do not enable live payments until sandbox webhook delivery, refund behavior, recovery paths and integration tests have been verified.

## Event delivery

Booking outbox events publish to `railreserve.booking.events`. The notification consumer stores event IDs in a durable inbox to deduplicate Kafka redelivery and creates notification-job records. Email/SMS provider delivery is not yet connected; jobs can remain WAITING_FOR_RECIPIENT.

## Errors and limitations

- `400`: invalid input.
- `401`: missing/invalid/expired token or credentials.
- `403`: authenticated user lacks access.
- `409`: booking, idempotency or unique-resource conflict.
- `429`: rate limit exceeded.
- `503`: auth rate limiter unavailable.

Payment collection/refunds, verified train data, email/SMS provider delivery, account verification/recovery, production TLS/secrets management and comprehensive concurrency/integration/load testing are still release blockers. This API reference documents the current implementation, not a claim of production certification.
