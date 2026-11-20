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
- `V5__create_bookings.sql` — bookings, partial unique constraints for live allocations and booking events
- `V6__support_direct_bookings.sql` — optional enquiry linkage and customer/assigned-salesperson snapshots for both booking paths
- `V7__create_booking_quotations.sql` — immutable tax-exclusive booking quotation snapshots and manager approval

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
| GET | /api/v1/users/salespeople | Salesperson names for enquiry assignment (admin: all, manager: own branch, salesperson: self) |

### Create three test-role accounts using the Expo UI

The Expo web/mobile app already has a shared sign-in screen. It appears when `EXPO_PUBLIC_API_URL` is set to a reachable Spring Boot HTTPS endpoint and the browser has loaded the updated bundle. After signing in as an **ADMIN**, open the **Users** tab. The administrator can view existing accounts and create other administrators, branch managers and salespeople; passwords are stored server-side as BCrypt hashes. The Users tab is not shown to non-admin users, and `/api/v1/users` rejects unauthorized requests.

For a first-time development database, bootstrap the initial admin by starting Spring Boot with `BOOTSTRAP_ADMIN_USERNAME` and a **unique strong** `BOOTSTRAP_ADMIN_PASSWORD` (at least 12 characters), as described above. Bootstrapping runs only when the users table is empty. Restarting with different bootstrap variables will not reset the password of an existing user.

For role-based testing, create a **Branch Manager** and a **Salesperson** assigned to the same test branch (for example, Varanasi), and give each account a unique test password. Sign out and sign back in with each account to verify their allowed modules and actions. Do **not** put test passwords in repository files, screenshots or `EXPO_PUBLIC_` variables.

The current interface supports user creation and listing, not password resets, disabling users or changing existing roles. These controls require a later security milestone.


Example: create a **test salesperson** using the admin account after startup. You can use `curl -u admin` to enter the password interactively; no password is embedded in the shell command or repository:

```bash
curl -u admin -H 'Content-Type: application/json' \
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

The vehicle API is now persisted in PostgreSQL. The Expo **LiveInventory** screen now uses these authenticated endpoints when `EXPO_PUBLIC_API_URL` is configured. The Expo app now uses one shared development sign-in screen and the same in-memory HTTP Basic transport for CRM and Inventory. Signing out or receiving HTTP 401 clears the in-memory credentials, and a reload requires signing in again.

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

## Sales & Bookings API — direct and enquiry conversion

The booking API supports **two workflows** and uses the same inventory reservation, manager confirmation, cancellation and auditing rules for each.

1. **Enquiry → Booking:** A salesperson opens an existing accessible enquiry in CRM and selects **Continue to booking**, which opens Sales with that enquiry preselected. The API copies customer name, mobile, branch and assigned salesperson from the enquiry; clients must not override these fields.
2. **Direct Booking:** A salesperson opens Sales → **Direct booking**, enters customer name and a 10-digit mobile number, and selects an available chassis in their branch. No dummy enquiry is created, and `enquiryId` remains `null`.

| Method | Route | Purpose |
|---|---|---|
| GET | /api/v1/bookings | List bookings visible to signed-in user's role and branch |
| GET | /api/v1/bookings/{id} | Read an accessible booking |
| GET | /api/v1/bookings/{id}/events | Audit lifecycle history |
| POST | /api/v1/bookings | Create either linked or direct pending booking |
| POST | /api/v1/bookings/{id}/confirm | Confirm pending booking (manager/admin) |
| POST | /api/v1/bookings/{id}/cancel | Cancel with a reason and release stock (manager/admin) |

**From enquiry:**

```json
{"enquiryId":123,"chassis":"CHASSIS-001"}
```

**Direct booking:**

```json
{"chassis":"CHASSIS-002","customerName":"Demo Walk-in","customerPhone":"9876543210"}
```

The request must follow exactly one path: an `enquiryId` or direct customer fields, not both. The customer fields are required and validated for direct bookings. A booking stores a customer snapshot even when an enquiry exists so historical details remain available. Direct bookings do not affect enquiry stages. A linked booking changes the enquiry to `Booked` on confirmation and restores its previous stage on cancellation.

A booking starts `PENDING` and reserves its vehicle. Authorized branch managers or administrators can confirm it to `CONFIRMED`. They can cancel pending or confirmed bookings with a reason, resulting in `CANCELLED` and releasing the chassis. A salesperson can create bookings for their own accessible enquiries or directly for a vehicle in their assigned branch, and view those bookings. The server enforces all branch and access rules; UI gating is not security.

Two concurrent bookings cannot allocate the same chassis; database partial unique constraints and row locks prevent duplicate active allocations. Only one active booking per enquiry is permitted. Generic inventory release refuses to release a vehicle allocated to an active booking.

**Not yet supported:** customer master/deduplication, quotation/prices, booking payment and refund, sale/delivery, invoice/GST, booking expiry, and production authentication. Use test customer data only.

## Payments, receipts, invoices and delivery — milestone 15

Flow: **quotation approved → booking confirmed → payments → GST invoice → full payment → delivery**. Migration `V8__create_billing.sql` adds `payment_receipts`, `invoices`, `document_sequences` and the `DELIVERED` booking status.

| Method | Route | Who | Purpose |
|---|---|---|---|
| GET | /api/v1/bookings/{id}/account | anyone who can see the booking | Payable, received, balance, invoice and receipts |
| POST | /api/v1/bookings/{id}/payments | salesperson (own), manager, admin | Record a payment; returns a numbered receipt |
| POST | /api/v1/bookings/{id}/payments/{receiptId}/void | manager, admin | Void a receipt with a reason (refund/mistake) |
| GET | /api/v1/bookings/{id}/invoice | anyone who can see the booking | Read the invoice |
| POST | /api/v1/bookings/{id}/invoice | manager, admin | Issue the one tax invoice for a confirmed booking |
| POST | /api/v1/bookings/{id}/deliver | manager, admin | Hand over: booking `DELIVERED`, vehicle `SOLD` |
| GET | /api/v1/finance/summary?month=YYYY-MM | all roles, scoped | Invoiced, collected, by-mode, outstanding dues, advances, deliveries, dues list, recent receipts |

Rules enforced by the server:

- Payments need an **approved quotation**, a live booking, and may not exceed the balance. Before invoicing, the payable is an estimate (quotation + 5% GST); afterwards it is the invoice total.
- Modes: `CASH`, `UPI`, `CARD`, `BANK_TRANSFER`, `CHEQUE`, `FINANCE`. Non-cash payments need a reference; finance disbursements need the finance company. **Cumulative cash on one booking must stay below ₹2,00,000** (Income Tax Act s.269ST). Received dates cannot be in the future or before the booking.
- Receipts are never deleted; managers void them with a reason.
- Invoices: one per booking, immutable, numbered `INV/<BRANCH>/<FY>/<seq>`; receipts are `RCT/<BRANCH>/<FY>/<seq>`. Numbers are gapless per branch and Indian financial year (April–March).
- GST: taxable value = approved quotation subtotal. Default rate 5% (EVs); same-state supply splits into CGST + SGST, other-state uses IGST. Total is rounded to the nearest rupee with a round-off line. Optional customer GSTIN is validated (15 characters).
- An invoiced booking, or one with live receipts, cannot be cancelled (void/refund receipts first; credit notes are not supported yet).
- Delivery requires the invoice and a zero balance.
- Error responses use RFC 9457 Problem Details; the reason is in `detail`.
- Invoices also record (all optional): relation and father's/guardian's name (S/o, D/o, W/o, C/o), another ID (PAN, driving licence, voter ID, passport), **only the last 4 Aadhaar digits** (full Aadhaar numbers are never stored), financier, and a subsidy/incentive deducted after GST. Vehicle frame, motor, every battery, charger and controller serials are copied onto the invoice.
- Dealer details printed on invoices come from environment settings, not code: `DEALER_GSTIN`, `DEALER_PHONE`, `DEALER_ADDRESS`, with optional per-branch `DEALER_ADDRESS_<BRANCH>` / `DEALER_PHONE_<BRANCH>` (e.g. `DEALER_ADDRESS_GHAZIPUR`). They are copied onto each invoice when issued.

**Sales report:** `GET /api/v1/reports/sales?from=YYYY-MM-DD&to=YYYY-MM-DD&branch=&model=&salesperson=` (admin: all branches or one; branch manager: own branch only; salespeople: 403). Lists every invoiced sale (app and paper) in the invoice-date range with customer, vehicle, taxable value, GST, subsidy, total, received, balance, payment modes and financier, plus totals, breakdowns by model/branch/salesperson and the filter options. The app shows it under Finance → Sales report with date presets and exports CSV for Excel (dd/mm/yyyy dates).

**Dates:** the API and database use ISO dates (`YYYY-MM-DD`, stored as SQL `DATE`/`TIMESTAMPTZ`); the app shows and accepts **dd/mm/yyyy** (Indian time) and converts at the edge, so no stored data needs migrating.

**Branches (V11 migration):** branches live in the `branches` table with the address printed on their invoices; `GET /api/v1/branches` lists active ones for the app. Seeded: **Atraoli-Ghazipur** (Atraoli (Maharana Pratap ke Bagal Me) Ghazipur) and **Naini-Prayagraj** (LIC Campus, Mewala Ki Bagiya Naini, Prayagraj). Users, vehicle registration, transfers and past sales must use an active branch. Dealer GSTIN `09ACZPM2545H1ZO` and mobile `9695551902` are the defaults for every branch (`app.dealer.*`). V11 also performed the one-time go-live cleanup that deleted all development test records except administrator accounts.

**Past sales from the paper invoice book (V10 migration):** `POST /api/v1/past-sales` (branch manager for their own branch, or admin) records a sale that already happened: customer and KYC fields, the vehicle (existing available stock, or new details recorded straight as `SOLD`, with any number of batteries), the **paper book and invoice number**, the original **invoice date**, the **delivery date** and each **payment with its real date**. It is stored as a delivered booking with an approved quotation, an invoice with `source = PAPER` (numbered `PAPER/<BRANCH>/<book>/<no>`, unique per branch, never consuming an app `INV` number) and receipts numbered from it. Revenue, collections and deliveries count in the months they happened; any balance shows as pending dues and can be collected later like any other payment. Printed copies are labelled *Duplicate copy — recorded from paper invoice*. Dates cannot be in the future; delivery cannot precede the invoice; the cash limit and serial uniqueness still apply.

**Multiple batteries (V9 migration):** a vehicle has one or more battery packs (`batterySerials: [...]`, up to 6), stored in `vehicle_batteries`; each serial is unique across all stock. The single `batterySerial` field is still accepted when registering.

**Not production-ready:** HSN codes, e-invoicing (IRN), credit notes, refunds as separate entries, accessory-specific GST rates, insurance and registration are not implemented. Have the dealership's accountant review GST (including how subsidies are shown) before real use.

## Quotation & pricing — milestone 12

Each pending booking can have **one immutable draft quotation**, optionally approved by the branch manager or administrator. The Expo Sales booking detail now provides fields to save and review quotations and an **Approve quotation** action for managers.

| Method | Route | Purpose |
|---|---|---|
| GET | /api/v1/bookings/{id}/quotation | Read an authorized booking's quotation |
| POST | /api/v1/bookings/{id}/quotation | Save one draft quotation for a pending booking |
| POST | /api/v1/bookings/{id}/quotation/approve | Manager/admin approval of the draft |

Example `POST /api/v1/bookings/123/quotation`:

```json
{
  "vehiclePrice": 105000.25,
  "discount": 5000.10,
  "accessories": 2250.25,
  "otherCharges": 750.00
}
```

The server computes `subtotalExcludingTax = vehiclePrice - discount + accessories + otherCharges` using Java `BigDecimal`, without floating-point rounding or more than two decimal places. Vehicle price must be positive; discount cannot exceed price; all other amounts must be nonnegative; resulting subtotal must be positive. All fields are mandatory, with zero values permitted where appropriate. The database independently checks amount constraints and subtotal arithmetic.

Only users authorized to see the booking can see the quotation. A salesperson can draft a quotation on an accessible pending booking; only a manager of its branch or an administrator can approve it. The quotation stores creator, approver, approval timestamp and booking events. Replacing, editing or re-approving the saved quotation returns a conflict; quotation revisioning is a later milestone.

**Important limitation:** The amounts are **internal, tax-exclusive indicative figures**, not a final customer payable price, tax calculation, official quotation document or GST invoice. GST, RTO registration, insurance, road tax, subsidies, financing costs, refunds and customer payments are NOT computed. Quotation approval is not currently a prerequisite to confirming a booking: existing booking workflows remain backward-compatible. Never use these figures as a legally compliant invoice or for real customer collection.

## Automated tests

```bash
cd services/api
mvn test          # Controller unit/web-slice tests with mocked repositories
mvn verify        # Unit tests + PostgreSQL Testcontainers integration tests
```

The `QuotationMathTest`, `BookingQuotationControllerTest`, `BookingQuotationServiceTest` and quotation portion of `BookingPersistenceIT` cover exact arithmetic, amount validation, PostgreSQL persistence, duplicate prevention, authorization and manager approval. The `BookingControllerTest`, `BookingServiceTest` and `BookingPersistenceIT` cover linked and direct booking creation, validation, state transitions, duplicate allocations, cancellation release, role/branch security and PostgreSQL persistence. The `InventoryPolicyTest`, `InventoryControllerTest` and `InventoryPersistenceIT` cover role policy, request validation, registration, uniqueness, reservations, transfer, receipt and movement history. The `EnquiryPersistenceIT` suite starts its **own ephemeral PostgreSQL container** and exercises full create, read, stage, follow-up and activity-history operations. It never uses the development database.

An optional **read-only** smoke test checks an already-running API:

```bash
RUN_LIVE_SMOKE=true mvn -Dit.test=EnquiryLiveSmokeIT failsafe:integration-test failsafe:verify
```

GitHub Actions runs `mvn clean verify` on backend changes.

## Connecting the Expo web preview (development only)

The Expo app requires `EXPO_PUBLIC_API_URL`; there is no local demo mode.

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
