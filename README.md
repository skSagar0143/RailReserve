# RailReserve

A production-oriented railway reservation platform with Spring Boot microservices, React web, React Native mobile, MySQL, Redis, and Kafka.

> Status: foundation bootstrap. Production readiness requires completing the implementation, running CI and security checks, and validating deployment configuration against real infrastructure.

## Architecture

- `backend/` — Java 21 / Spring Boot services and shared contracts
- `frontend/web/` — React + TypeScript + Vite glass UI
- `frontend/mobile/` — React Native + Expo mobile application
- `infra/` — local development Compose, observability and deployment templates
- `docs/` — architecture, API, security, operations and development guides

## Core design rules

1. MySQL is the source of truth for bookings and seat inventory.
2. Booking writes use database transactions and explicit concurrency protection; never rely on Redis locks alone for correctness.
3. Kafka events use stable event IDs, idempotent consumers, bounded retries and dead-letter handling.
4. Credentials and JWT signing keys come from environment/secret management, never source control.
5. CI must compile, test, scan and build artifacts before deployment.

## Initial setup

See `docs/DEVELOPMENT.md` for local prerequisites and staged setup. Never use production credentials in local `.env` files.

## Project status

This repository is being built in increments. A checked-in scaffold is not a claim that every feature has been implemented or production-verified. Follow `docs/ROADMAP.md` and the CI results before deploying.
