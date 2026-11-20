# Purvanchal Electroride ERP

A multi-branch electric-vehicle dealership management system for new EVs, targeting approximately 3–5 branches.

> **Development status:** The Expo dashboard, enquiry CRM and vehicle inventory interfaces exist. The Spring Boot API supports PostgreSQL-backed enquiries, chassis-level inventory, booking allocations and draft quotations, with development-only database-backed authentication and branch-scoped access. Enquiries and Inventory use persistent APIs when configured; the Sales & Bookings API supports enquiry-linked and direct bookings, with an authenticated Expo Sales screen. Finance remains a demo placeholder. **No real customer or financial data should be used yet.**

## Architecture

| Layer | Technology | Status |
|---|---|---|
| Mobile and web preview | Expo SDK 58, React Native, TypeScript | Dashboard and opt-in API-backed CRM, inventory and bookings |
| API | Java 21, Spring Boot 3.5 | CRM, inventory, bookings and quotation endpoints |
| Database | PostgreSQL 16 + Flyway | Enquiries, users, vehicles, bookings and tax-exclusive quotation records |
| Integration tests | JUnit 5, Spring Boot Test, Testcontainers | CRM, security, inventory and booking lifecycle tests |
| CI | GitHub Actions | Backend unit/integration checks and Expo TypeScript checks |
| Web admin | React/TypeScript | Planned |
| Development authentication | Spring Security, BCrypt and database-backed users | Implemented; not production-ready |

## Repository layout

```text
apps/mobile/       Expo mobile app and web preview
services/api/      Spring Boot REST API, migrations, automated tests
infra/             Local PostgreSQL Docker Compose
.github/workflows/ GitHub Actions backend CI
```

## Functional scope

- Customer enquiries, assignments, follow-ups, activities and conversion tracking
- Vehicle procurement, unique chassis and component-serial tracking
- Branch transfers, inventory reservations and sales allocation
- Bookings, invoices, deliveries, returns and cancellations
- Cash, UPI, bank transfer and finance/loan receipts
- Finance-company disbursement and reconciliation
- Double-entry accounting, GST, expenses and receivables
- Configurable government subsidy claimant/recipient flows
- Branch-scoped roles, audit trails and reporting

## Start the project in Codespaces

**Database:**

```bash
docker compose -f infra/docker-compose.yml up -d
```

**Backend:** Run from the repository root in a second terminal.

```bash
cd services/api
BOOTSTRAP_ADMIN_USERNAME=admin BOOTSTRAP_ADMIN_PASSWORD='REPLACE_WITH_STRONG_TEST_PASSWORD' mvn spring-boot:run
```

**Expo web preview:** Run from the repository root in another terminal.

```bash
cd apps/mobile
npm ci
npx expo start --web --host localhost
```

Open the forwarded **8081** port in Codespaces. The app needs `EXPO_PUBLIC_API_URL` (see below); there is no offline demo mode.

### Single-port preview via same-origin proxy

When only one forwarded port is convenient (or cross-origin calls to port 8080 are blocked), serve the app and API from one origin:

```bash
echo 'EXPO_PUBLIC_API_URL=/backend' > apps/mobile/.env.local   # ignored file, no secrets
node scripts/dev-proxy.mjs                                      # port 3000: /backend/* → 8080, rest → 8081
```

Start Spring Boot and Expo as above (no `APP_CORS_ALLOWED_ORIGIN` needed), then open port **3000**. Keep it private.

### Public test deployment (Render)

The root `Dockerfile` builds one image: the Expo web app is exported with `EXPO_PUBLIC_API_URL=/` and served by Spring Boot, so UI and API share one HTTPS origin (no CORS). `render.yaml` adds a PostgreSQL database.

1. In Render: **New → Blueprint**, connect this GitHub repository and select the branch.
2. When prompted, enter a unique `BOOTSTRAP_ADMIN_PASSWORD` (12+ characters). It is stored only in Render.
3. After deploy, open `https://<service>.onrender.com`, sign in as `admin`, and create manager/salesperson accounts in the **Users** tab.

Free-tier notes: the service sleeps after ~15 minutes idle (first request then takes about a minute), and the free database expires after 30 days. **Test data only:** HTTP Basic development authentication is publicly reachable on this URL and is not production-ready.

To build locally: `docker build -t electroride .` then run it with `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` and the bootstrap variables.

### Codespaces phone preview (private forwarding)

To automatically configure the ignored Expo `.env.local` with the current Codespace's private HTTPS API URL (port 8080), run from the repository root:

```bash
bash scripts/configure-codespaces-preview.sh
```

The script uses `CODESPACE_NAME`, so it works if the Codespace name differs from previous sessions. It prints the frontend origin and the `APP_CORS_ALLOWED_ORIGIN` startup command for Spring Boot. Restart Spring Boot with that variable and restart Expo on port 8081 to use the new URL. Keep both ports **Private**. Do not commit development credentials. Private Codespaces port authentication can still block cross-origin API calls; if that occurs, use a private same-origin proxy instead of exposing the API publicly.

### Test logins and role management

When `EXPO_PUBLIC_API_URL` is configured, the app shows a shared sign-in screen for all modules. The backend already supports three roles: `ADMIN` (all branches), `BRANCH_MANAGER` (assigned branch) and `SALESPERSON` (own assigned enquiries and bookings). Log in with the initial development admin account to access the new **Users** tab (web sidebar or mobile bottom navigation), where you can list accounts and create additional test administrators, branch managers or salespeople. Branch is required for non-admin accounts. Then sign out and log in under each role to test the restrictions. Passwords are never embedded in source code; use only synthetic account data. See the backend README for the one-time administrator bootstrap instructions.

### Optional API-backed CRM and Inventory

All screens, including the dashboard, use the real API configured by `EXPO_PUBLIC_API_URL` in `apps/mobile/.env.local`; the former in-memory demo screens and hardcoded dashboard figures have been removed. The Inventory screen supports vehicle registration, branch-scoped viewing, reservation, release, transfer initiation, destination receipt and movement history. Administrators and branch managers can mutate authorized stock; salespeople can only view it. This is an **opt-in development preview** with one app-wide sign-in screen for CRM and Inventory. The account's role and branch are obtained once from `/api/v1/auth/me`, retained in React state while the app is running, and passed to both modules. Credentials are held in memory only; sign-out or an HTTP 401 clears them and returns the app to sign-in. A page reload also requires signing in again. An initial admin account is bootstrapped once from the backend environment variables when no users exist. This is not a production authentication design.

The Sales screen is API-connected as well, including an internal draft quotation view for pending bookings and manager approval. A salesperson can continue an enquiry into a booking using **Continue to booking**, or start a **Direct booking** from Sales without first creating an enquiry. Both paths allocate a specific available chassis and record customer details; managers confirm or cancel bookings. This is a booking/allocation workflow only, not a completed sale or invoice. See **[API setup, booking endpoints, and Codespaces CORS limitations](services/api/README.md)**. Keep the API port private. An authenticated Codespaces forwarding proxy may be required for the cross-origin web preview; do not expose the API publicly to bypass forwarding restrictions.

## End-to-end flow check

`scripts/flow-test.py` creates managers and salespeople in Varanasi and Ghazipur, registers vehicles, creates enquiries and walks the enquiry → booking → quotation → confirmation, cancellation and branch-transfer flows, checking each role restriction (57 checks). Use only against a test deployment:

```bash
BASE_URL=https://<your-service>.onrender.com ADMIN_PASSWORD='<admin password>' \
TEST_PASSWORD='<12+ char password for created accounts>' python3 scripts/flow-test.py
```

## Automated tests

Backend tests:


```bash
cd services/api
mvn test      # Controller tests
mvn verify    # Controller + isolated PostgreSQL integration tests
```

Expo frontend unit tests and TypeScript checks:

```bash
cd apps/mobile
npm ci
npm run typecheck
npm test
```

`sharedSession.test.mjs` verifies a single authenticated header is used across CRM and inventory requests, logout clears it, and HTTP 401 resets the session. These are development-only client tests; authorization decisions remain enforced by Spring Boot.

GitHub Actions runs backend tests when backend files change and both TypeScript checks and frontend unit tests when mobile files change. These checks must pass before treating new functionality as verified.

## Development milestones

1. **Done — Preview foundation:** dashboard and responsive navigation.
2. **Done — Demo CRM:** enquiry creation, status and local follow-ups with a role-visibility simulation.
3. **Done — Demo inventory:** vehicle registration, chassis/serial validation, reservations and transfers.
4. **Done — Backend foundation:** Spring Boot, Flyway, PostgreSQL and first API endpoints.
5. **Implemented, verification pending — Persistent CRM:** API-backed enquiry list, creation, activity history, stage changes and follow-ups, with automated tests.
6. **Implemented, verification pending — Development security:** database-backed users, admin provisioning, HTTP Basic login, enforced server-side CRM branch/salesperson visibility and automated role tests.
7. **Backend CI verified — Inventory backend:** persistent chassis and component serials, reservation, branch transfer, receipt, movement history and server-side branch enforcement.
8. **Implemented, new CI verification pending — Inventory UI integration:** API-connected inventory with role-aware controls and automated frontend rule tests (offline demo screens since removed).
9. **Implemented, CI pending — Shared development login:** a single Expo sign-in, shared role/branch identity, centralized logout and automatic reset on HTTP 401; mobile unit tests cover credential handling.
10. **Implemented — Sales & Bookings foundation:** booking allocation, confirmation, cancellation, audit events and PostgreSQL uniqueness protection.
11. **Implemented, new CI verification pending — Direct and enquiry-linked bookings:** Flyway V6 migration, direct customer capture without a dummy enquiry, CRM → Sales handoff, authenticated Sales UI, and unit/PostgreSQL tests.
12. **Implemented, CI verification pending — Quotation and pricing foundation:** immutable quotation per pending booking, exact decimal calculations, manager approval, audit events, PostgreSQL V7 and UI integration. Figures exclude taxes and are NOT final customer prices.
13. **Implemented, CI pending — Admin user management UI:** create and list test users for the three supported roles from an authenticated Admin-only screen; existing app-wide login remains shared.
14. **Implemented — Live dashboard and real salesperson picker:** demo data removed.
15. **Implemented — Payments, receipts, GST invoices and delivery:** numbered receipts (cash/UPI/card/bank/cheque/finance), 269ST cash limit, immutable invoices with CGST/SGST or IGST and round-off, printable invoice/receipt (web), delivery marks stock SOLD, Finance tab with collections and dues. GST treatment needs accountant review.
16. **Implemented — Dashboard money cards and past sales:** month cards from real invoices/receipts; managers record back-dated sales from the paper invoice book with original numbers and dates.
17. **Next —** credit notes and refunds, price lists, accounting and finance reconciliation, production authentication.

## Safety and compliance

This is development software, not a production ERP. The backend enforces role-based CRM, inventory and booking permissions and requires development credentials. The standalone demo screens are still simulations, and the application does not yet have production-ready authentication or administrative audit controls. Financial and GST rules, subsidy treatment, backup processes, privacy protection and operational procedures require implementation, testing and review before production use.
