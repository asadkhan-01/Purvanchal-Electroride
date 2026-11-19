# Electroride API — development only

Java 21 + Spring Boot 3.5 + PostgreSQL 16 + Flyway.

**Not production ready:** endpoints have no authentication or branch-level authorisation yet. Do not expose port 8080 publicly or use real customer data.

## Start in Codespaces

From the repository root:

```bash
docker compose -f infra/docker-compose.yml up -d
cd services/api
mvn spring-boot:run
```

If `mvn` is unavailable, install Maven in the Codespace or add a Maven wrapper.

In another terminal:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/enquiries
curl -X POST http://localhost:8080/api/v1/enquiries \
  -H 'Content-Type: application/json' \
  -d '{"customerName":"Demo Customer","phone":"9876500000","branch":"Varanasi","salesperson":"Aman","source":"Walk-in","interestedModel":"Electric Scooter"}'
```

## Current scope

- PostgreSQL persistence for enquiries
- Flyway V1 migration
- GET /api/v1/enquiries
- GET /api/v1/enquiries/{id}
- POST /api/v1/enquiries
- PATCH /api/v1/enquiries/{id}/stage

## Before any real usage

Implement authentication, branch-scoped authorization, audit logging, activity/follow-up tables, request limits, CORS configuration, error handling, tests, backups and secure secrets. The Expo CRM screen still uses in-memory demo data and is **not** connected to this API.
