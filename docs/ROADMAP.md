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
- [x] Initial JUnit/Mockito tests and CI workflow for backend, web, mobile and infrastructure.
- [x] JaCoCo per-module and aggregate HTML/XML coverage reporting, with test and coverage reports uploaded as a CI artifact.

## Still required before production

- [x] CI backend verification, web build, mobile type-check, infrastructure validation and JaCoCo aggregation passed on commit `551fdd05e5f6cbd16d119218aeab6f813ed5edda`; CodeQL also passed on that commit.
- [ ] Re-run CI after the latest added boundary tests; run migrations and the entire Compose stack in an integration environment.
- [ ] Raise measured coverage substantially from the last verified aggregate baseline: instructions 13.65%, branches 21.74%, lines 18.12%, methods 15.50%, classes 22.58%.
- [ ] Expand coverage to auth success/refresh/reuse, booking transaction and concurrency edge cases, Kafka outbox/inbox retry behavior, and API integration tests; set coverage thresholds after measuring a trustworthy baseline.
- [ ] Add payment provider integration, verified fare/route data, payment webhook signature validation, refund/reconciliation and expiry cleanup operations.
- [ ] Add key-ID-aware RSA rotation/JWKS, account verification/recovery, account/device management and stronger abuse controls.
- [ ] Complete role/ownership tests, integration tests, Testcontainers, API contract tests and concurrent oversell/idempotency tests.
- [x] ADMIN-only seat inventory and server-configured fare setup.
- [ ] Add train/seat update and cancellation workflows, admin audit trails, and measure/tune cache hit rate and invalidation under load.
- [ ] Add a real notification provider adapter, recipient resolution, delivery retries, provider idempotency and operator visibility for failed jobs.
- [x] Connect web and mobile clients to live train search, registration/login, access-token refresh, seat holds and booking history; keep tokens in memory rather than localStorage.
- [ ] Persist theme/font preferences, improve offline/retry UX, and add the actual payment checkout flow.
- [ ] Add API gateway, OpenAPI, service identity, TLS and authenticated/encrypted Redis/Kafka, least-privilege DB accounts and managed production secrets.
- [ ] Add structured audit logs, metrics/tracing/alerts, dependency/SBOM/secret/container scans, load tests, backup restore drills, disaster recovery and rollback/release rehearsal.
- [ ] Perform independent threat modeling and security review; confirm applicable railway/payment/privacy requirements.

This is still an implementation in progress, not a production release. Do not deploy to real customers or process real payments until the remaining gates are implemented, tested, reviewed and validated.
