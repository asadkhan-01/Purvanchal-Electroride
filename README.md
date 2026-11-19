# Purvanchal Electroride ERP

Multi-branch electric vehicle dealership management system.

## Project status

Initial development. The Expo mobile app is being scaffolded in GitHub Codespaces; modules below are planned and not yet production-ready.

## Technology

- Mobile: React Native, Expo, TypeScript
- Web admin: React, TypeScript (planned)
- API: Java, Spring Boot (planned)
- Database: PostgreSQL (planned)

## Modules

- Customer enquiries, assignments, follow-ups and CRM
- Vehicle procurement and chassis-level inventory
- Component serial numbers and inter-branch transfers
- Bookings, invoicing, payments and delivery
- Finance-company disbursements and bank reconciliation
- Double-entry accounting, GST and expense management
- Configurable government subsidy workflows
- Reports, user roles, branch permissions and audit trail

## Run Expo app

```bash
cd apps/mobile
npm install
npx expo install react-dom react-native-web
npx expo start --web --host 0.0.0.0
```

In GitHub Codespaces, open the forwarded web port from the **Ports** tab.

## Implementation roadmap

1. Expo setup and dashboard
2. Enquiry CRM
3. Vehicle inventory and procurement
4. Sales, booking and delivery
5. Finance, payments and reconciliation
6. Accounting, GST and subsidies
7. Administration, reporting and production hardening

> Accounting and subsidy rules require verification and professional review before live use.
