# Security controls and production release gates

## Implemented in the current codebase

- Registration and login with BCrypt password hashing (work factor 12); registration assigns only the CUSTOMER role.
- RS256 JWT access tokens with issuer, audience, issue time, expiry, subject, role and token ID claims.
- RSA keys loaded from mounted PEM files; startup rejects RSA signing keys smaller than 3072 bits and rejects mismatched public/private keys.
- JWT signature, issuer, audience and standard timestamp validation in the auth service and resource-server configuration in the other backend services.
- Opaque refresh tokens stored as SHA-256 hashes, rotation, expiry, logout revocation and a database lock while rotating a token.
- Stateless API security, health-only public actuator access, CORS allow-list configuration and read-only secret mounts in local Compose.
- Input DTO validation and generic unauthorized responses.

## Required before production traffic

This project is not yet production-ready. Finish and verify all of the following before launch:

- Add key-ID-aware rotation/JWKS publication; define key rotation, overlap and emergency revocation procedures.
- Add account email verification, password recovery/change, session/device management, refresh-token reuse detection, account lockout and distributed rate limiting.
- Add explicit role and resource-ownership checks to every business endpoint; never trust a customer ID supplied by the client.
- Set environment-specific exact CORS origins; terminate TLS at a hardened gateway; configure request size/time limits, trusted-proxy rules, HSTS and other security headers.
- Move production keys and credentials into a secret manager/KMS; use separate least-privilege database users and authenticated/encrypted Kafka and Redis connections.
- Add audit events without secrets/PII, centralized structured logs, metrics, tracing, alerting, log retention and incident response.
- Implement booking transactions with database locking/atomic inventory updates, idempotency, transactional outbox, Kafka retries/DLT and consumer deduplication.
- Add comprehensive unit/integration/contract tests, dependency/SBOM/secret/container scans, migration/restore rehearsals, concurrency tests, load tests and rollback drills.
- Perform an independent security review and threat model. Do not use this application to sell tickets or process real customer data until these gates pass.

## RSA key setup for local development

From the repository root, run:

```powershell
.\infra\scripts\generate-rsa-keys.ps1
```

The script requires OpenSSL 3 and creates ignored files under `infra/secrets`. Never commit private keys. Rotate by using a documented key-ID-aware overlap process, not by replacing a live key without a migration plan.
