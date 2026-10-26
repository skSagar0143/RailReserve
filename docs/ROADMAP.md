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
- [x] Re-run CI after payment implementation: commit `23542e2ebbf3adb63449ae2e9b1e4029373419c7` passed backend Maven verification/JaCoCo, web build, mobile type-check, and infrastructure validation. Re-run again after the latest webhook deduplication commits; still required: migrations and the entire Compose stack in an integration environment.
- [ ] Raise measured coverage substantially from the last verified aggregate baseline: instructions 13.65%, branches 21.74%, lines 18.12%, methods 15.50%, classes 22.58%.
- [ ] Expand coverage to auth success/refresh/reuse, booking transaction and concurrency edge cases, Kafka outbox/inbox retry behavior, refund provider outcomes, and API integration tests; set coverage thresholds after measuring a trustworthy baseline.
- [x] Add Razorpay hosted payment-link checkout, owner-checked payment status, signed webhook verification, amount/currency matching, idempotent booking confirmation, web/mobile checkout actions, and provider event-ID deduplication when `X-Razorpay-Event-Id` is supplied.
- [x] Add an ADMIN-only full-refund action for captured Razorpay payments flagged as paid-after-expiry or amount/currency mismatch, durable refund request tracking, one refund request per payment, and reconciliation visibility for pending/uncertain refund outcomes. Confirmed bookings cannot be refunded through this reconciliation endpoint. Ambiguous provider outcomes are not retried automatically to avoid duplicate refunds.
- [ ] Validate the refund flow in Razorpay sandbox, reconcile provider-side status after timeouts, and add provider-mocked integration tests. Live credentials and live refunds remain unverified.
- [ ] Add key-ID-aware RSA rotation/JWKS, account verification/recovery, account/device management and stronger abuse controls.
- [ ] Complete role/ownership tests, integration tests, Testcontainers, API contract tests and concurrent oversell/idempotency tests.
- [x] ADMIN-only seat inventory and server-configured fare setup.
- [ ] Complete train/seat update and cancellation workflows, audit trails for all administrative mutations (payment refund actions are now audited), and measure/tune cache hit rate and invalidation under load.
- [ ] Add a real notification provider adapter, recipient resolution, delivery retries, provider idempotency and operator visibility for failed jobs.
- [x] Connect web and mobile clients to live train search, registration/login, access-token refresh, seat holds and booking history; keep tokens in memory rather than localStorage.
- [ ] Persist theme/font preferences and improve offline/retry UX. Web/mobile payment checkout flow is implemented; end-to-end provider validation remains outstanding.
- [ ] Add API gateway, OpenAPI, service identity, TLS and authenticated/encrypted Redis/Kafka, least-privilege DB accounts and managed production secrets.
- [ ] Add structured audit logs, metrics/tracing/alerts, dependency/SBOM/secret/container scans, load tests, backup restore drills, disaster recovery and rollback/release rehearsal.
- [ ] Perform independent threat modeling and security review; confirm applicable railway/payment/privacy requirements.

Payment setup: set `PAYMENT_ENABLED=true`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, and `RAZORPAY_WEBHOOK_SECRET` in the deployment environment. Configure Razorpay to send the `payment_link.paid` event to `/api/v1/payments/webhook`. Start with test-mode credentials. The backend never trusts a browser redirect as proof of payment; only a signature-verified webhook with matching amount/currency confirms a booking. Payments received after the seat hold expires or with mismatched amount/currency are held for manual reconciliation and are not automatically refunded yet.

Webhook deduplication migration `V5__payment_webhook_deduplication.sql` records provider event IDs so duplicate deliveries can be ignored when the gateway supplies the event ID header. If the header is absent, provider-event-level deduplication is not available.

This is still an implementation in progress, not a production release. Do not deploy to real customers or process live payments until CI, sandbox webhook delivery, refund/reconciliation, integration tests, security review, and operational recovery have been completed.
