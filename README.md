# Purvanchal Electroride ERP

A multi-branch electric-vehicle dealership management system for new EVs, targeting approximately 3–5 branches.

> **Development status:** The Expo dashboard, enquiry CRM and vehicle inventory interfaces exist. The Spring Boot API supports PostgreSQL-backed enquiries, chassis-level inventory and booking allocations, with development-only database-backed authentication and branch-scoped access. Enquiries and Inventory use persistent APIs when configured; the Sales & Bookings API supports enquiry-linked and direct bookings, with an authenticated Expo Sales screen. Finance remains a demo placeholder. **No real customer or financial data should be used yet.**

## Architecture

| Layer | Technology | Status |
|---|---|---|
| Mobile and web preview | Expo SDK 58, React Native, TypeScript | Dashboard and opt-in API-backed CRM, inventory and bookings |
| API | Java 21, Spring Boot 3.5 | CRM, inventory and bookings endpoints |
| Database | PostgreSQL 16 + Flyway | Enquiries, activities, users, vehicles, movements and direct/linked bookings |
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

Open the forwarded **8081** port in Codespaces. The web preview works in demo mode without backend configuration.

### Optional API-backed CRM and Inventory

The `LiveCRM.tsx` and `LiveInventory.tsx` screens call the real API when `apps/mobile/.env.local` contains `EXPO_PUBLIC_API_URL`. Without this configuration, `CRM.tsx` and `Inventory.tsx` continue to use in-memory demo data. The Inventory screen supports vehicle registration, branch-scoped viewing, reservation, release, transfer initiation, destination receipt and movement history. Administrators and branch managers can mutate authorized stock; salespeople can only view it. This is an **opt-in development preview** with one app-wide sign-in screen for CRM and Inventory. The account's role and branch are obtained once from `/api/v1/auth/me`, retained in React state while the app is running, and passed to both modules. Credentials are held in memory only; sign-out or an HTTP 401 clears them and returns the app to sign-in. A page reload also requires signing in again. An initial admin account is bootstrapped once from the backend environment variables when no users exist. This is not a production authentication design.

The Sales screen is API-connected as well. A salesperson can continue an enquiry into a booking using **Continue to booking**, or start a **Direct booking** from Sales without first creating an enquiry. Both paths allocate a specific available chassis and record customer details; managers confirm or cancel bookings. This is a booking/allocation workflow only, not a completed sale or invoice. See **[API setup, booking endpoints, and Codespaces CORS limitations](services/api/README.md)**. Keep the API port private. An authenticated Codespaces forwarding proxy may be required for the cross-origin web preview; do not expose the API publicly to bypass forwarding restrictions.

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
8. **Implemented, new CI verification pending — Inventory UI integration:** API-connected inventory with role-aware controls and automated frontend rule tests; demo fallback remains.
9. **Implemented, CI pending — Shared development login:** a single Expo sign-in, shared role/branch identity, centralized logout and automatic reset on HTTP 401; mobile unit tests cover credential handling.
10. **Implemented — Sales & Bookings foundation:** booking allocation, confirmation, cancellation, audit events and PostgreSQL uniqueness protection.
11. **Implemented, new CI verification pending — Direct and enquiry-linked bookings:** Flyway V6 migration, direct customer capture without a dummy enquiry, CRM → Sales handoff, authenticated Sales UI, and unit/PostgreSQL tests.
12. **Next —** quotes, pricing and booking payments; then invoices, delivery, GST, accounting, finance reconciliation and production hardening.

## Safety and compliance

This is development software, not a production ERP. The backend enforces role-based CRM, inventory and booking permissions and requires development credentials. The standalone demo screens are still simulations, and the application does not yet have production-ready authentication or administrative audit controls. Financial and GST rules, subsidy treatment, backup processes, privacy protection and operational procedures require implementation, testing and review before production use.
