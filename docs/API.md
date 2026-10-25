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

The server locks inventory rows, verifies all seats belong to the requested train/date, computes the total from configured seat fares, marks seats HELD, stores the booking and outbox event in one database transaction. The current status is PENDING_PAYMENT because no payment provider is integrated yet. Unpaid holds expire automatically.

### Booking history/details/cancellation
- `GET /api/v1/bookings/mine`
- `GET /api/v1/bookings/{bookingId}`
- `POST /api/v1/bookings/{bookingId}/cancel` — only pending-payment holds can be cancelled through this endpoint.

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
