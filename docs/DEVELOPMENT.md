# Local development

Prerequisites: Java 21, Maven 3.9+, Node.js 22, npm, Docker Desktop Compose v2 and OpenSSL 3. Copy `.env.example` to `.env` at repository root and replace all sample passwords with random local values. Never commit `.env`.

Before starting backend services, generate local RSA keys:

```powershell
.\infra\scripts\generate-rsa-keys.ps1
```

The script writes private/public PEM files to `infra/secrets`, which is git-ignored. Auth service receives the private and public key; other services receive only the public key. Do not use these local keys in production.

Run `docker compose --env-file .env -f infra/docker-compose.yml up --build` from repository root. API gateway: `http://localhost:8080`. Web: copy `frontend/web/.env.example` to `frontend/web/.env`, then `cd frontend/web; npm install; npm run dev`. Mobile: copy `frontend/mobile/.env.example` to `frontend/mobile/.env`, then `cd frontend/mobile; npm install; npm start`. The mobile API URL depends on the device: Android emulator uses `http://10.0.2.2:8080`, iOS simulator typically uses `http://localhost:8080`, and a physical device needs your computer's LAN IP and matching firewall access. Backend: `mvn -B verify` from repository root.

Local infrastructure ports: MySQL 3306, Redis 6379, Kafka host listener 29092. Gateway port: 8080. Service ports (local debugging only): auth 8081, train 8082, booking 8083, notification 8084. The gateway does not terminate TLS; production must place it behind a properly configured TLS-capable ingress/load balancer and restrict direct service access.

Auth API:
- `POST /api/v1/auth/register` — create a CUSTOMER account.
- `POST /api/v1/auth/login` — authenticate.
- `POST /api/v1/auth/refresh` — rotate a refresh token.
- `POST /api/v1/auth/logout` — revoke a refresh token.
- `GET /api/v1/auth/me` — requires `Authorization: Bearer <access-token>`.

Train APIs:
- `GET /api/v1/stations?q=NDL` — search active stations.
- `GET /api/v1/trains/search?from=NDLS&to=BCT&date=2026-11-02` — search scheduled train routes.

Administrator provisioning and APIs are documented in [ADMIN_BOOTSTRAP.md](ADMIN_BOOTSTRAP.md). Booking APIs require an access token and use the authenticated token subject as the customer ID. A seat must have a positive server-configured fare before it can be booked.

The system is still under development: no payment gateway or real email/SMS provider is configured, and preview cards are illustrative. Do not expose infrastructure or Actuator publicly.
