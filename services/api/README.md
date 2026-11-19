# Purvanchal Electroride API

Java 21 · Spring Boot 3.5 · PostgreSQL 16 · Flyway

**Development only.** The API now uses database-backed Spring Security HTTP Basic authentication and enforces enquiry read/write scope by role and branch. This is **not production-grade authentication** (no MFA, login throttling, secure session lifecycle, credential rotation or enterprise audit yet). Keep API port 8080 private and use test data only.

## Start in GitHub Codespaces

From repository root:

```bash
docker compose -f infra/docker-compose.yml up -d
cd services/api
BOOTSTRAP_ADMIN_USERNAME=admin BOOTSTRAP_ADMIN_PASSWORD='REPLACE_WITH_A_STRONG_RANDOM_PASSWORD' mvn spring-boot:run
```

The server uses port 8080 and reads PostgreSQL connection settings from `DB_URL`, `DB_USER`, and `DB_PASSWORD`. Default credentials are local development placeholders only.

The initial admin is created **only when** `app_users` contains no users and both bootstrap variables are supplied. Choose a unique password of at least 12 characters; never commit it. On subsequent restarts the existing password remains unchanged. If users already exist, no account is automatically added. The bootstrap credentials are for initial development setup only.

Database migrations run automatically at startup:

- `V1__create_enquiries.sql` — enquiries
- `V2__create_enquiry_activities.sql` — append-only enquiry activity messages
- `V3__create_app_users.sql` — username, BCrypt password hash, role, assigned branch and enabled state
- `V4__create_inventory.sql` — chassis-level vehicles, component serial constraints and movement events

## Authentication and permissions

All `/api/v1/**` endpoints require an authenticated user.

| Role | Enquiry scope | Allowed creation |
|---|---|---|
| `ADMIN` | All branches | Any branch and salesperson |
| `BRANCH_MANAGER` | Assigned branch | Assigned branch |
| `SALESPERSON` | Their branch and matching assigned salesperson name | Only their own name in their branch |

Enquiry reads, activity history, follow-up updates and stage changes are restricted server-side. Inaccessible records return 404 to avoid revealing their existence. User management is admin-only.

Current development authentication: `Authorization: Basic <base64(username:password)>` over HTTPS for remote previews. The mobile app holds credentials only in memory and clears them on logout. Use a production-ready authentication architecture before releasing the app.

| Method | Route | Purpose |
|---|---|---|
| GET | /api/v1/auth/me | Authenticated user identity and scope |
| GET | /api/v1/users | Admin-only user listing (no password hashes) |
| POST | /api/v1/users | Admin-only provisioning |

Example: create a **test salesperson** using the admin account after startup. You can use `curl -u admin` to enter the password interactively; no password is embedded in the shell command or repository:

```bash
curl -u admin -H 'Content-Type: application/json' \\
  -d '{"username":"aman","password":"REPLACE_WITH_A_DIFFERENT_STRONG_PASSWORD","displayName":"Aman","role":"SALESPERSON","branch":"Varanasi"}' \\
  http://localhost:8080/api/v1/users
```

Do not use the literal placeholder password or real identities. Passwords entered as literal JSON in commands may remain in shell history; use test-only credentials for this development exercise.

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

## Vehicle inventory API — milestone 7

The vehicle API is now persisted in PostgreSQL. The Expo **LiveInventory** screen now uses these authenticated endpoints when `EXPO_PUBLIC_API_URL` is configured; without that variable, the original inventory demo is displayed. The Expo app now uses one shared development sign-in screen and the same in-memory HTTP Basic transport for CRM and Inventory. Signing out or receiving HTTP 401 clears the in-memory credentials, and a reload requires signing in again.

| Method | Route | Purpose |
|---|---|---|
| GET | /api/v1/inventory | List accessible vehicles |
| GET | /api/v1/inventory/{chassis} | Get vehicle by chassis |
| GET | /api/v1/inventory/{chassis}/movements | Read transfer and reservation events |
| POST | /api/v1/inventory | Register vehicle and component serials |
| POST | /api/v1/inventory/{chassis}/reserve | Reserve an available vehicle |
| POST | /api/v1/inventory/{chassis}/release | Release reservation |
| POST | /api/v1/inventory/{chassis}/transfer | Start transfer to destination branch |
| POST | /api/v1/inventory/{chassis}/receive | Confirm receiving branch receipt |

Registration requires `chassis`, `model`, `variant`, `branch` and the unique `motorSerial`, `batterySerial`, `chargerSerial`, and `controllerSerial`. Chassis and serials are normalized to uppercase and constrained unique at the database level.

The transfer state is `AVAILABLE → IN_TRANSIT → AVAILABLE`: `branch` stays as the source until receipt, and `transferDestination` records the destination. The destination branch manager or administrator confirms receipt. Only administrators and managers may modify stock; salespeople can view stock scoped to their branch, including inbound transfers.

Invalid status transitions return 409. Duplicate chassis or component serials return 409. An inaccessible vehicle returns 404. Mutations record movement history and use pessimistic row locks to avoid concurrent state changes. **The API does not implement sale completion, purchasing, cancel-transfer, reservation-to-customer linkage, or inventory valuation yet.**

## Automated tests

```bash
cd services/api
mvn test          # Controller unit/web-slice tests with mocked repositories
mvn verify        # Unit tests + PostgreSQL Testcontainers integration tests
```

The `InventoryPolicyTest`, `InventoryControllerTest` and `InventoryPersistenceIT` cover role policy, request validation, registration, uniqueness, reservations, transfer, receipt and movement history. The `EnquiryPersistenceIT` suite starts its **own ephemeral PostgreSQL container** and exercises full create, read, stage, follow-up and activity-history operations. It never uses the development database.

An optional **read-only** smoke test checks an already-running API:

```bash
RUN_LIVE_SMOKE=true mvn -Dit.test=EnquiryLiveSmokeIT failsafe:integration-test failsafe:verify
```

GitHub Actions runs `mvn clean verify` on backend changes.

## Connecting the Expo web preview (development only)

The Expo CRM and Inventory screens use the API only when `EXPO_PUBLIC_API_URL` is configured; otherwise they stay in local demo mode.

For a Codespaces browser preview:

1. Run the API on port 8080 and the Expo web preview on port 8081.
2. In Codespaces **Ports**, copy the HTTPS forwarded URL for **8080** (the API) and **8081** (the Expo page). Keep both ports private.
3. From `apps/mobile`, create ignored `.env.local` with:

   ```env
   EXPO_PUBLIC_API_URL=https://YOUR-CODESPACE-8080.app.github.dev
   ```

4. Start the API with an explicit, exact web origin in `APP_CORS_ALLOWED_ORIGIN` (no path or trailing slash):

   ```bash
   APP_CORS_ALLOWED_ORIGIN=https://YOUR-CODESPACE-8081.app.github.dev \\\n   BOOTSTRAP_ADMIN_USERNAME=admin BOOTSTRAP_ADMIN_PASSWORD='REPLACE_WITH_A_STRONG_RANDOM_PASSWORD' \\\n   mvn spring-boot:run
   ```

5. Restart Expo so it picks up the environment variable:

   ```bash
   npx expo start --web --host localhost
   ```

Restart both the backend and Expo after changing the environment. The Expo app prompts once for credentials and reads role/branch identity from `GET /api/v1/auth/me`, sharing it between CRM and Inventory. Inventory can be accessed read-only by salespeople; only assigned managers and administrators can perform stock changes. Sign-out and HTTP 401 require reauthentication. Navigation between workspaces does not.

**Important browser limitation:** Private Codespaces forwarded URLs can require GitHub authorization. Cross-origin `fetch` may be redirected or blocked even with API CORS enabled. If that occurs, keep ports private, return to demo mode, and use a same-origin authenticated development proxy once introduced. **Do not change port 8080 to public** to bypass this restriction.

Any variable prefixed `EXPO_PUBLIC_` is embedded in the client bundle. It must **never** contain passwords, tokens or private secrets.

## Pending before production

Production-grade authentication (short-lived tokens or secure cookie sessions, MFA, password reset, rate limiting), role administration, activity author attribution, paging, audit logging, backups, origin restrictions and complete penetration/security testing. HTTP Basic and disabled CSRF protection are development-only choices.
