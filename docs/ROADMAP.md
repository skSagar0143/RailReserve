# Roadmap and current status

## Implemented foundation

- [x] Repository structure, four Spring Boot service modules and health endpoints.
- [x] Initial MySQL migrations for identity, stations/trains/stops, seat inventory, bookings and transactional outbox.
- [x] React glass dashboard with color and font selectors, and clearly labelled illustrative data.
- [x] React Native Expo starter with theme and text-size controls.
- [x] Local MySQL, Redis and Kafka Compose configuration, health checks, non-root Java image and ignored local secrets.
- [x] Customer registration/login, BCrypt password hashing, RS256 JWT access tokens, issuer/audience/time validation, hashed refresh tokens, rotation, logout revocation and Redis-backed auth route limits and refresh-token family reuse detection.
- [x] RSA public-key JWT verification in train, booking and notification services; configurable CORS allow-list.
- [x] Initial station search endpoint.
- [x] Date-aware train route search and ADMIN-only station/train/stop/service-date setup APIs.
- [x] Transactional booking API foundation with server-configured seat fares, row locking, 10-minute seat holds, idempotency keys, cancellation of unpaid holds and outbox event persistence.
- [x] Scheduled Kafka outbox publisher with idempotent producer settings.
- [x] Notification inbox deduplication, durable notification job records and retry/dead-letter topic configuration.
- [x] Initial JUnit/Mockito tests and CI workflow for backend, web and mobile.

## Still required before production

- [ ] Make CI fully green on the current commit; run migrations and the entire Compose stack locally.
- [ ] Add payment provider integration, verified fare/route data, payment webhook signature validation, refund/reconciliation and expiry cleanup operations.
- [ ] Add key-ID-aware RSA rotation/JWKS, account verification/recovery, account/device management and stronger abuse controls.
- [ ] Complete role/ownership tests, integration tests, Testcontainers, API contract tests and concurrent oversell/idempotency tests.
- [x] ADMIN-only seat inventory and server-configured fare setup.
- [ ] Add train/seat update and cancellation workflows, admin audit trails, caching strategy and cache invalidation.
- [ ] Add a real notification provider adapter, recipient resolution, delivery retries, provider idempotency and operator visibility for failed jobs.
- [x] Connect web and mobile clients to live train search, registration/login, access-token refresh, seat holds and booking history; keep tokens in memory rather than localStorage.
- [ ] Persist theme/font preferences, improve offline/retry UX, and add the actual payment checkout flow.
- [ ] Add API gateway, OpenAPI, service identity, TLS and authenticated/encrypted Redis/Kafka, least-privilege DB accounts and managed production secrets.
- [ ] Add structured audit logs, metrics/tracing/alerts, dependency/SBOM/secret/container scans, load tests, backup restore drills, disaster recovery and rollback/release rehearsal.
- [ ] Perform independent threat modeling and security review; confirm applicable railway/payment/privacy requirements.

This is still an implementation in progress, not a production release. Do not deploy to real customers or process real payments until the remaining gates are implemented, tested, reviewed and validated.
