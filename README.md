# RailReserve

RailReserve is a railway reservation platform under active development. The repository is being built as a Java 21 / Spring Boot microservice system with a React + TypeScript glass UI and a React Native mobile client.

## Current repository contents

- `backend/auth-service` — auth service module foundation, security baseline and initial identity schema
- `backend/train-service` — train service module and station search API
- `backend/booking-service` — booking module foundation, seat inventory / booking / outbox schema
- `backend/notification-service` — notification service foundation with Kafka configuration
- `frontend/web` — responsive glass dashboard, station/date search form, theme combinations and font selector
- `frontend/mobile` — Expo React Native starter with theme and text-size controls
- `infra` — local MySQL, Redis and Kafka Compose setup plus a non-root Java image
- `docs` — architecture, local setup, security release gates and implementation roadmap

## Core correctness and security rules

1. MySQL is the source of truth for bookings and seat inventory.
2. Booking operations must use transactions and explicit concurrency protection; Redis locks alone are not sufficient.
3. Booking requests must be idempotent, and booking events should use a transactional outbox plus event-ID deduplication.
4. Credentials and signing keys must come from environment variables or a secret manager, never source control.
5. CI must compile, test, scan and validate artifacts before deployment.

## Local setup

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md). Copy `.env.example` to `.env` and replace every sample password before starting Compose.

## Important status notice

This is an implementation in progress, **not yet production-ready**. The web and mobile train listings contain clearly labelled illustrative data. Secure registration/login, JWT issuance, complete train schedule search, seat booking/cancellation workflows, Kafka outbox publishing and consumers, payment integrations, comprehensive automated tests, production observability and deployment hardening still require implementation and verification. Do not use this build to sell tickets or process real customer data.

Track remaining work in [docs/ROADMAP.md](docs/ROADMAP.md) and security gates in [docs/SECURITY.md](docs/SECURITY.md).
