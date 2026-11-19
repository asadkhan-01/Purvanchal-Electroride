# Purvanchal Electroride API

Java 21 · Spring Boot 3.5 · PostgreSQL 16 · Flyway

**Development only.** The API currently has **no authentication or server-enforced branch access**. Never expose it to the public internet or use real customer information.

## Start in GitHub Codespaces

From repository root:

```bash
docker compose -f infra/docker-compose.yml up -d
cd services/api
mvn spring-boot:run
```

The server uses port 8080 and reads PostgreSQL connection settings from `DB_URL`, `DB_USER`, and `DB_PASSWORD`. Default credentials are local development placeholders only.

Database migrations run automatically at startup:

- `V1__create_enquiries.sql` — enquiries
- `V2__create_enquiry_activities.sql` — append-only enquiry activity messages

## CRM endpoints

| Method | Route | Purpose |
|---|---|---|
| GET | /api/v1/enquiries | List enquiries |
| GET | /api/v1/enquiries/{id} | Read enquiry |
| POST | /api/v1/enquiries | Create enquiry |
| PATCH | /api/v1/enquiries/{id}/stage | Change stage and record an activity |
| PATCH | /api/v1/enquiries/{id}/follow-up | Set a follow-up date and record an activity |
| GET | /api/v1/enquiries/{id}/activities | Read activity history |
| POST | /api/v1/enquiries/{id}/activities | Add activity or call outcome |

Follow-up dates use ISO `YYYY-MM-DD`. Stages are `New`, `Contacted`, `Interested`, `Booked` or `Lost`.

## Automated tests

```bash
cd services/api
mvn test          # Controller unit/web-slice tests with mocked repositories
mvn verify        # Unit tests + PostgreSQL Testcontainers integration tests
```

The `EnquiryPersistenceIT` suite starts its **own ephemeral PostgreSQL container** and exercises full create, read, stage, follow-up and activity-history operations. It never uses the development database.

An optional **read-only** smoke test checks an already-running API:

```bash
RUN_LIVE_SMOKE=true mvn -Dit.test=EnquiryLiveSmokeIT failsafe:integration-test failsafe:verify
```

GitHub Actions runs `mvn clean verify` on backend changes.

## Connecting the Expo web preview (development only)

The Expo CRM screen uses the API only when `EXPO_PUBLIC_API_URL` is configured; otherwise it stays in local demo mode.

For a Codespaces browser preview:

1. Run the API on port 8080 and the Expo web preview on port 8081.
2. In Codespaces **Ports**, copy the HTTPS forwarded URL for **8080** (the API) and **8081** (the Expo page). Keep both ports private.
3. From `apps/mobile`, create ignored `.env.local` with:

   ```env
   EXPO_PUBLIC_API_URL=https://YOUR-CODESPACE-8080.app.github.dev
   ```

4. Start the API with an explicit, exact web origin in `APP_CORS_ALLOWED_ORIGIN` (no path or trailing slash):

   ```bash
   APP_CORS_ALLOWED_ORIGIN=https://YOUR-CODESPACE-8081.app.github.dev mvn spring-boot:run
   ```

5. Restart Expo so it picks up the environment variable:

   ```bash
   npx expo start --web --host localhost
   ```

**Important browser limitation:** Private Codespaces forwarded URLs can require GitHub authorization. Cross-origin `fetch` may be redirected or blocked even with API CORS enabled. If that occurs, keep ports private, return to demo mode, and use a same-origin authenticated development proxy once introduced. **Do not change port 8080 to public** to bypass this restriction.

Any variable prefixed `EXPO_PUBLIC_` is embedded in the client bundle. It must **never** contain passwords, tokens or private secrets.

## Pending before production

Authentication, proper branch-scoped authorization, activity author attribution, transaction boundaries for all mutations, CORS policy, server-side paging and filtering, API error handling, rate limits, audit logging, encrypted backups, security testing and API tests for role permissions.
