# Local development

Prerequisites: Java 21, Maven 3.9+, Node.js 22, npm and Docker Desktop Compose v2. Copy .env.example to .env at repository root and replace all sample passwords with random local values. Never commit .env.

Run docker compose --env-file .env -f infra/docker-compose.yml up from repository root. Web: cd frontend/web, npm install, npm run dev. Mobile: cd frontend/mobile, npm install, npm start. Backend: mvn -B verify from repository root.

Local infrastructure ports: MySQL 3306, Redis 6379, Kafka host listener 29092. Service ports: auth 8081, train 8082, booking 8083, notification 8084.

The services currently provide application and health foundations. Business APIs, live train feeds and booking workflows are not implemented yet; preview data is illustrative, not live availability. Do not expose infrastructure or Actuator publicly.
