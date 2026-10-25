# RailReserve

RailReserve is an actively developed railway reservation platform using Java 21, Spring Boot microservices, MySQL, Redis, Kafka, a React + TypeScript web UI and a React Native mobile starter.

## Repository modules

- `backend/auth-service` — customer registration/login, BCrypt, RS256 JWTs, refresh-token rotation/revocation and Redis-backed auth route rate limits.
- `backend/train-service` — station search API and RSA JWT verification.
- `backend/booking-service` — seat availability, transactional seat holds, server-configured fares, idempotent booking requests, unpaid-hold cancellation and transactional outbox publishing to Kafka.
- `backend/notification-service` — Kafka consumer with event-ID deduplication, durable inbox/job records, retry and dead-letter configuration. A real email/SMS provider adapter is not yet connected.
- `frontend/web` — responsive glass dashboard with color combinations and font selection; train results remain illustrative.
- `frontend/mobile` — Expo React Native starter with theme and text-size controls.
- `infra` — local MySQL, Redis, Kafka Compose setup, Nginx API gateway, RSA key generation helper and non-root Java image.
- `docs` — architecture, local setup, security gates and implementation roadmap.

## Important correctness/security rules

1. MySQL is the source of truth for seat inventory and bookings. Redis is used for rate limiting/cache support, never as the booking correctness authority.
2. Booking rows and outbox events are committed in one transaction. Kafka delivery can repeat; consumers deduplicate by event ID.
3. Seat fares must be configured server-side. The API rejects booking when a seat has no positive configured fare; it never trusts a client-supplied total.
4. Never commit private signing keys, passwords or production secrets. The auth service needs the RSA private key; other services receive only the public key.
5. A green build is necessary but not sufficient for production. Concurrency, payment, security, recovery, observability and release checks must also pass.

## Local setup

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md). Copy `.env.example` to `.env`, replace sample passwords and generate local RSA keys with `infra/scripts/generate-rsa-keys.ps1` from PowerShell. Never use these local keys in production.

## Production status

**Not production-ready yet.** Some foundational flows are implemented, but the complete train schedule/admin system, payment and refund lifecycle, real notification delivery, web/mobile API integration, key rotation, account recovery, full security and concurrency tests, production TLS/secrets/observability, and verified deployment process remain unfinished. CI must be green on the latest commit and the full system must be tested before any real customer use.

See [docs/ROADMAP.md](docs/ROADMAP.md), [docs/SECURITY.md](docs/SECURITY.md), and [docs/ADMIN_BOOTSTRAP.md](docs/ADMIN_BOOTSTRAP.md) for release gates and safe administrator setup.
