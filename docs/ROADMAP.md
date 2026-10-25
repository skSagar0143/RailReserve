# Roadmap and current status

- [x] Repository structure, four Spring Boot service modules and health endpoints.
- [x] Initial MySQL migrations for identity, trains/stops, seat inventory, bookings and outbox.
- [x] React glass dashboard with color and font selectors, and clearly labelled illustrative data.
- [x] React Native Expo starter with theme and text-size controls.
- [x] Local MySQL, Redis and Kafka Compose configuration.
- [ ] Implement secure registration/login, JWT, roles and security tests.
- [ ] Implement station/train/route/schedule APIs and real search/cache invalidation.
- [ ] Implement transactional seat booking/cancellation, concurrency tests, idempotency, outbox publisher, Kafka consumers, retries and dead-letter handling.
- [ ] Add API gateway, OpenAPI, service identity, integration tests, JUnit/Mockito and Testcontainers.
- [ ] Connect both clients to real APIs; add auth flows, persisted preferences and error/retry UX.
- [ ] Add observability, secrets/TLS, vulnerability scanning, deployment manifests, load tests, backup restore drills and release rehearsal.

A scaffold is not a completed production system. Do not deploy to real users until required features are implemented, tested, reviewed and validated.
