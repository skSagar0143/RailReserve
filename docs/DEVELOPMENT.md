# Local development

Prerequisites: Java 21, Maven 3.9+, Node.js 22, npm, Docker Desktop Compose v2 and OpenSSL 3. Copy `.env.example` to `.env` at repository root and replace all sample passwords with random local values. Never commit `.env`.

Before starting backend services, generate local RSA keys:

```powershell
.\infra\scripts\generate-rsa-keys.ps1
```

The script writes private/public PEM files to `infra/secrets`, which is git-ignored. Auth service receives the private and public key; other services receive only the public key. Do not use these local keys in production.

Run `docker compose --env-file .env -f infra/docker-compose.yml up --build` from repository root. Web: `cd frontend/web; npm install; npm run dev`. Mobile: `cd frontend/mobile; npm install; npm start`. Backend: `mvn -B verify` from repository root.

Local infrastructure ports: MySQL 3306, Redis 6379, Kafka host listener 29092. Gateway: http://localhost:8080. Service ports (local debugging only): auth 8081, train 8082, booking 8083, notification 8084. Use the gateway for browser/client requests; it does not terminate TLS and must sit behind a TLS-capable load balancer or reverse proxy in production.

Auth API:
- `POST /api/v1/auth/register` — create a CUSTOMER account.
- `POST /api/v1/auth/login` — authenticate.
- `POST /api/v1/auth/refresh` — rotate a refresh token.
- `POST /api/v1/auth/logout` — revoke a refresh token.
- `GET /api/v1/auth/me` — requires `Authorization: Bearer <access-token>`.

The services currently provide application and health foundations. Train schedules and booking workflows are not fully implemented; preview data is illustrative, not live availability. Do not expose infrastructure or Actuator publicly.
