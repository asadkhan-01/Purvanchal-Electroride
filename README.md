# Purvanchal Electroride ERP

A multi-branch electric-vehicle dealership management system for new EVs, targeting approximately 3–5 branches.

> **Development status:** The Expo dashboard, enquiry CRM and vehicle inventory previews exist. The Spring Boot API supports PostgreSQL-backed enquiries, stages, scheduled follow-ups, activity history and development-only database-backed authentication with branch-scoped access. Inventory, sales and finance are still demo screens. **No real customer or financial data should be used yet.**

## Architecture

| Layer | Technology | Status |
|---|---|---|
| Mobile and web preview | Expo SDK 58, React Native, TypeScript | Dashboard, demo inventory and CRM |
| API | Java 21, Spring Boot 3.5 | CRM endpoints |
| Database | PostgreSQL 16 + Flyway | Enquiries and activities |
| Integration tests | JUnit 5, Spring Boot Test, Testcontainers | CRM lifecycle tests |
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

### Optional API-backed CRM

The new `LiveCRM.tsx` screen calls the real API when `apps/mobile/.env.local` contains `EXPO_PUBLIC_API_URL`. This is an **opt-in development preview** that now prompts for a username and password. An initial admin account is bootstrapped once from the backend environment variables when no users exist. This is not a production authentication design.

See **[API setup, endpoint details and Codespaces CORS limitations](services/api/README.md)**. Keep the API port private. An authenticated Codespaces forwarding proxy may be required for the cross-origin web preview; do not expose the API publicly to bypass forwarding restrictions.

## Run automated backend tests

```bash
cd services/api
mvn test      # Controller tests
mvn verify    # Controller + isolated PostgreSQL integration tests
```

GitHub Actions runs the backend tests when backend files change and also typechecks the Expo app when mobile files change. These checks must pass before treating new functionality as verified.

## Development milestones

1. **Done — Preview foundation:** dashboard and responsive navigation.
2. **Done — Demo CRM:** enquiry creation, status and local follow-ups with a role-visibility simulation.
3. **Done — Demo inventory:** vehicle registration, chassis/serial validation, reservations and transfers.
4. **Done — Backend foundation:** Spring Boot, Flyway, PostgreSQL and first API endpoints.
5. **Implemented, verification pending — Persistent CRM:** API-backed enquiry list, creation, activity history, stage changes and follow-ups, with automated tests.
6. **Implemented, verification pending — Development security:** database-backed users, admin provisioning, HTTP Basic login, enforced server-side CRM branch/salesperson visibility and automated role tests.
7. **Later — Persistent inventory, sales, finance, accounting, GST and deployment.**

## Safety and compliance

This is development software, not a production ERP. The backend now enforces role-based CRM permissions and requires development credentials. The demo role selector is still not security, and the application does not yet have production-ready authentication or administrative audit controls. Financial and GST rules, subsidy treatment, backup processes, privacy protection and operational procedures require implementation, testing and review before production use.
