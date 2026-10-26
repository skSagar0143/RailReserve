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

## Latest reliability fixes (2026-10-26)

- [x] Fix mobile authenticated requests to honor an explicitly supplied fresh access token. This prevents the post-login seat-availability request from being overwritten by a stale empty token.
- [x] Keep the same booking idempotency key when retrying the same web/mobile seat-hold request, and reset the key when the user changes the selection or starts a new search. This reduces duplicate holds after ambiguous network failures.
- [x] Deduplicate exact payment-webhook body retries even when the provider event-ID header is absent, using a SHA-256 body fingerprint; provider event IDs remain preferred when supplied.
- [x] Add refund regression tests for returning an existing refund intent and rejecting a payment that is not eligible for refund.
- [x] CI verification passed for webhook-fingerprint changes, audited fare updates, train service-date cancellation, train/station status controls, and filtered admin audit history. The latest main-branch CodeQL scan also passed on commit `841148d50bd50bad218138c2b918a77c2b8c2427`.

## Still required before production

- [x] Latest full CI run on commit `841148d50bd50bad218138c2b918a77c2b8c2427` passed backend Maven verification, web build, mobile type-check, infrastructure validation, and a full Docker Compose smoke test. The smoke test successfully applied Flyway migrations in all four MySQL schemas and verified all four services healthy through the Nginx gateway. CodeQL also passed on this commit.
- [ ] Raise measured coverage substantially from the latest verified aggregate report on commit `841148d50bd50bad218138c2b918a77c2b8c2427`: instructions 32.73%, branches 47.52%, lines 36.39%, methods 26.98%, classes 32.05%. These figures are verified CI measurements, not a claim that the coverage target is met.
- [ ] Expand coverage to auth success/refresh/reuse, booking transaction and concurrency edge cases, Kafka outbox/inbox retry behavior, refund provider outcomes, and API integration tests; set coverage thresholds after measuring a trustworthy baseline.
- [x] Add unit tests for auth email normalization/validation and current-user missing-account behavior, plus payment checkout/status identity validation and missing-provider-credential rejection. These are incremental edge-case tests, not a substitute for end-to-end/integration coverage; refresh/reuse, concurrency, and provider-timeout scenarios remain open.
- [x] Add payment unit tests for checkout ownership enforcement, expired seat holds, and payment-status ownership enforcement; CI passed on the latest verified main commit. These tests do not replace database/provider integration tests.
- [x] Add Razorpay hosted payment-link checkout, owner-checked payment status, signed webhook verification, amount/currency matching, idempotent booking confirmation, web/mobile checkout actions, and provider event-ID deduplication when `X-Razorpay-Event-Id` is supplied.
- [x] Add an ADMIN-only full-refund action for captured Razorpay payments flagged as paid-after-expiry or amount/currency mismatch, durable refund request tracking, one refund request per payment, and reconciliation visibility for pending/uncertain refund outcomes. Confirmed bookings cannot be refunded through this reconciliation endpoint. Ambiguous provider outcomes are not retried automatically to avoid duplicate refunds.
- [ ] Validate the refund flow in Razorpay sandbox, reconcile provider-side status after timeouts, and add provider-mocked integration tests. Live credentials and live refunds remain unverified.
- [ ] Add key-ID-aware RSA rotation/JWKS, account verification/recovery, account/device management and stronger abuse controls.
- [ ] Complete remaining role/ownership tests, database-backed API integration tests, Testcontainers, API contract tests and concurrent oversell/idempotency tests.
- [x] ADMIN-only seat inventory and server-configured fare setup.
- [x] Add a guarded admin fare-update endpoint: only AVAILABLE seats can be repriced, the row is locked during the update, fare precision is validated, and the mutation is recorded in the admin audit log. This does not permit repricing held/booked seats.
- [x] Add a guarded train service-date cancellation workflow: only future/current SCHEDULED service dates can be cancelled; the action is audited and the train-search cache version is invalidated after commit.
- [x] Add admin station/train activation controls; changes are audited and invalidate the train-search cache after commit.
- [x] Add a paginated (maximum 100 records) admin audit-event query with actor/action filters so operators can inspect recorded booking-service administrative changes.
- [x] Route admin payment reconciliation/refund and audit-event API paths through Nginx to the booking service rather than the train service.
- [ ] Complete remaining train/seat update workflows, audit trails for all administrative mutations, and measure/tune cache hit rate and invalidation under load.
- [ ] Add a real notification provider adapter, recipient resolution, delivery retries, provider idempotency and operator visibility for failed jobs.
- [x] Connect web and mobile clients to live train search, registration/login, access-token refresh, seat holds and booking history; keep tokens in memory rather than localStorage.
- [ ] Persist theme/font preferences and improve offline/retry UX. Web/mobile payment checkout flow is implemented; end-to-end provider validation remains outstanding.
- [ ] Add API gateway, OpenAPI, service identity, TLS and authenticated/encrypted Redis/Kafka, least-privilege DB accounts and managed production secrets.
- [ ] Add structured audit logs, metrics/tracing/alerts, dependency/SBOM/secret/container scans, load tests, backup restore drills, disaster recovery and rollback/release rehearsal.
- [ ] Perform independent threat modeling and security review; confirm applicable railway/payment/privacy requirements.

Payment setup: set `PAYMENT_ENABLED=true`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, and `RAZORPAY_WEBHOOK_SECRET` in the deployment environment. Configure Razorpay to send the `payment_link.paid` event to `/api/v1/payments/webhook`. Start with test-mode credentials. The backend never trusts a browser redirect as proof of payment; only a signature-verified webhook with matching amount/currency confirms a booking. Payments received after the seat hold expires or with mismatched amount/currency are held for manual reconciliation and are not automatically refunded yet.

Webhook deduplication migration `V5__payment_webhook_deduplication.sql` records provider event IDs so duplicate deliveries can be ignored when the gateway supplies the event ID header. If the header is absent, provider-event-level deduplication is not available.

This is still an implementation in progress, not a production release. Do not deploy to real customers or process live payments until CI, sandbox webhook delivery, refund/reconciliation, integration tests, security review, and operational recovery have been completed.
