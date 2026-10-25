# Security release checklist

This repository is a foundation, not a production security certification. Before real users or payments implement password hashing, account verification/recovery, rate limiting, JWT signing and key rotation, issuer/audience/expiry validation, resource-level authorization, strict CORS, request limits, security headers, TLS, secret management, least-privilege database/broker accounts, audit events, encrypted backups and restore drills. Add dependency, secret and container scans.

Test seat oversell, duplicate booking submissions, token replay, IDOR, brute force, cache staleness, Kafka redelivery, outbox crashes and personal-data leakage. Require code review, CI, migration rehearsal, load tests and rollback plan before release.
