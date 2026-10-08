# TransitDues - Living Spec

This file is a running account of what TransitDues is, how it is put together, and
what has been built so far, in the order it was built. It is meant to be updated as
the project grows, not rewritten from scratch each time - when something changes,
find the relevant section below and update it, and add a new dated entry under
"Evolution log" describing what changed and why.

This complements, but does not replace, `README.md` (setup instructions and the
course requirement mapping) and `HELP.md` (generated Spring Boot reference links).

## What the app is

TransitDues is a cooperative ledger for Kigali's moto-taxi stages. It tracks:

- **Stages**: physical taxi ranks (name, location, capacity).
- **Operators**: moto-taxi riders assigned to a stage (name, phone, plate number).
- **Due payments**: dues issued to operators (daily stage fee or quarterly tax),
  tracked through a lifecycle from issued to paid or overdue.

It exposes both a REST API (`/api/**`) and a server-rendered Thymeleaf web app
(`/web/**`, plus `/`, `/login`, `/register`, `/portal`), backed by PostgreSQL,
MongoDB, Redis and RabbitMQ, all running locally in Docker.

## Architecture

```
                         Browser
                      /          \
              Thymeleaf UI      REST API
               (/web/**)        (/api/**)
                      \          /
                 Spring MVC Controllers
                           |
                     Service Layer
          Stage / Operator / DuePayment / UserAccount /
                   Registration / DashboardStats
             |          |         |         |
         Postgres     Mongo     Redis    RabbitMQ
          (JPA)     (audit &   (cache)   (events)
                    event log)
```

Every mutation to a Stage, Operator, DuePayment or UserAccount goes through the
service layer, regardless of whether it arrived through the REST controllers or the
Thymeleaf web controllers. The service layer writes the primary record to
PostgreSQL, records an audit entry in MongoDB, evicts the Redis dashboard cache
where relevant, and (for due payments) publishes an event to RabbitMQ that a
separate consumer turns into another MongoDB audit record and a simulated
notification.

## Domain model (current)

- **Stage** (`stage.domain.Stage`): `name` (unique), `location`, `capacity`.
- **Operator** (`operator.domain.Operator`): `fullName`, `phoneNumber`,
  `plateNumber` (unique, normalized - see "Plate number handling" below), `stage`
  (many-to-one). No login credentials live here; a login is a separate
  `UserAccount` optionally linked to one.
- **UserAccount** (`user.domain.UserAccount`): `fullName`, `email` (unique, stored
  lowercase), `passwordHash`, `enabled`, `roles` (exactly one of ADMIN,
  FINANCE_OFFICER, OPERATOR - see "Roles" below), `operator` (nullable one-to-one,
  only ever set for an OPERATOR account).
- **DuePayment** (`duepayment.domain.DuePayment`): `amount`, `type` (DAILY /
  QUARTERLY_TAX), `status` (PENDING / PAID / OVERDUE), `dueDate`, `reference`
  (null until paid), `paidAt` (null until paid), `paymentMethod` (ONLINE / CASH,
  null until paid), `issuedBy`, `operator`. Unique on (`operator`, `type`,
  `dueDate`): the same due cannot be issued twice. See "Due payment lifecycle"
  below for the full story.
- **AuditLog** / **PaymentEventLog** (`audit.*`, MongoDB): append-only audit trail.
  `AuditLog` is written directly by every service mutation; `PaymentEventLog` is
  written by `PaymentAuditConsumer` reacting to RabbitMQ events.

## Roles

Three roles: `ADMIN`, `FINANCE_OFFICER`, `OPERATOR`. An account has **exactly one**
role - this is enforced centrally in `UserAccountService.save(...)`, which every
code path that creates or updates a `UserAccount` must go through (it throws
`MultipleRolesException` otherwise). An ADMIN or FINANCE_OFFICER account can never
also hold OPERATOR; there was briefly an admin-self-service feature that could
produce that combination ("Register me as an operator") - it has been removed
entirely (see Evolution log, Session 4).

| Role | How an account gets it | Can access |
|---|---|---|
| `ADMIN` | Seeded via `SEED_ADMIN_EMAIL`/`SEED_ADMIN_PASSWORD`, or Google login with an email in `OAUTH_ADMIN_EMAILS` | Dashboard, Stages, Operators, Due Payments (view only, cannot issue), Audit Log |
| `FINANCE_OFFICER` | Seeded via `SEED_FINANCE_EMAIL`/`SEED_FINANCE_PASSWORD`, or Google login with an email in `OAUTH_FINANCE_EMAILS` | Dashboard, Stages, Operators (read-only), Due Payments (full: issue, bulk issue, record payments) |
| `OPERATOR` | Self-registration at `/register`, or an admin creating an operator with a login | `/portal` only (own operator profile, dues) |
| no role | A Google login matching neither email list | Only `/login`, `/register`, `/access-denied` |

Database-backed accounts (seeded, self-registered, or admin-created) authenticate
by **email or phone** - `CustomUserDetailsService` tries email first, then falls
back to the phone number of the linked `Operator`. The resulting principal's
username is always the account's canonical email, regardless of which one was
typed, so `Authentication.getName()` is consistent either way. Google accounts
authenticate the normal OAuth2 way and are mapped to ADMIN/FINANCE_OFFICER only
(never OPERATOR) via `OAuth2UserRoleMapper`.

`RoleBasedAuthenticationSuccessHandler` sends a freshly authenticated session to
`/` if it holds ADMIN or FINANCE_OFFICER, or `/portal` otherwise (OPERATOR-only).

## Due payment lifecycle

A due starts life **PENDING** with a `dueDate`, issued by a FINANCE_OFFICER (ADMIN
can view but never issue - enforced by `@PreAuthorize("hasRole('FINANCE_OFFICER')")`
on every issuing/mutating endpoint). From there, two paths lead to PAID:

- **Finance records it directly** (sets `reference`, `paidAt`, `paymentMethod` via
  the edit form) - the original path, still available for e.g. backfilling a
  payment collected outside the app.
- **The operator pays it themselves from `/portal`** (added in Session 5, see
  below): PENDING/OVERDUE/FAILED &rarr; **SUBMITTED** (online payment started)
  &rarr; **PAID** or **FAILED** (operator confirms, outcome is simulated), or
  PENDING/OVERDUE/FAILED &rarr; **CASH_PENDING** (operator asks to pay cash) &rarr;
  PAID once a finance officer confirms it (that confirmation action is P5, not yet
  built - a CASH_PENDING due is a dead end in the UI until then, beyond the
  operator seeing "Awaiting finance confirmation").

**OVERDUE** happens automatically once `dueDate` has passed, while still PENDING -
unrelated to the payment-attempt states above, and a due can go straight from
OVERDUE into SUBMITTED/CASH_PENDING/PAID the same as PENDING can.

Two ways to issue:

1. **Issue one due** (`/web/duepayments/new`, `POST /web/duepayments`): operator,
   type, amount, due date. Rejects a duplicate (same operator, type, dueDate) with
   "This due is already issued for that operator and date."
2. **Bulk issue** (`/web/duepayments/bulk-issue`): type, amount, due date, and a
   scope (all operators, or every operator at one stage). Issues one PENDING due
   per operator, skipping anyone who already has that type+dueDate, in one
   transaction. Reports "Issued N dues, skipped M already issued."

The duplicate check lives once, centrally, in `DuePaymentServiceImpl` (not
duplicated per call site), and applies uniformly to single issue, bulk issue,
updates and the REST API, since they all funnel through the same service methods.
The database's unique constraint on (`operator_id`, `type`, `due_date`) is the last
line of defence for a race between two concurrent requests; its violation is
translated to the same friendly message (and only that constraint - any other
integrity violation surfaces as-is, it is not blanket-swallowed).

**Overdue is computed two ways, deliberately kept in sync:**

- `OverdueDuePaymentJob` is a daily `@Scheduled` job (cron `0 1 0 * * *`, zone
  `Africa/Kigali`, i.e. just after midnight) that flips any PENDING row whose
  `dueDate` has passed to OVERDUE in the database, writes an audit entry per row,
  and evicts the dashboard cache if anything changed. It also runs once at
  startup (`ApplicationRunner`), so a due that went overdue while the app was down
  is not left stale until the next midnight.
- `DuePayment.getEffectiveStatus()` is a `@Transient` computed getter: if the
  stored status is PENDING and `dueDate` is in the past, it reports OVERDUE on the
  spot, without waiting for the job to have run. The list page's status badge, its
  status filter, and the dashboard's status breakdown all read
  `getEffectiveStatus()`, not the raw `status` column, so the UI is never stale
  even in the gap between a due going overdue and the nightly job (or the
  startup run) catching up with it. The "collected" dashboard total specifically
  checks the real, persisted `status == PAID` (that one is never time-derived).

## Plate number handling

Operator plate numbers are normalized in `OperatorServiceImpl` - trimmed,
uppercased, spaces and hyphens removed - **before** validating or saving, so
`"rab 123a"`, `"RAB-123A"` and `"RAB123A"` are all the same plate. The duplicate
check (and this normalization) lives once in `OperatorServiceImpl.createOperator`/
`updateOperator`, and applies to self-registration, admin creation, update and the
REST API, all of which funnel through those two methods. No strict format is
enforced (no regex on the plate) - only uniqueness, case/whitespace-insensitively.
The database's unique constraint on `plate_number` is the backstop for the same
kind of race condition as above.

## Messaging, caching, audit (unchanged shape throughout)

- **RabbitMQ**: `DuePaymentEventPublisher` publishes `duepayment.created` /
  `duepayment.updated` to the `transitdues.events` topic exchange for every due
  payment create/update (single issue, bulk issue, and payment recording all go
  through the same publish call). `PaymentAuditConsumer` turns each event into a
  `PaymentEventLog` row; `NotificationConsumer` logs a simulated email/SMS.
- **Redis**: `dashboardStats` cache, 60s TTL, holds nothing but the
  `DashboardStats` record. Evicted on every Stage/Operator/DuePayment mutation and
  by the overdue job when it actually changes something.
- **MongoDB audit log**: every service-layer mutation calls
  `AuditLogService.record(...)`. For actions with no authenticated principal
  (operator self-registration), a fallback identity (the new account's own email)
  is passed through so the entry is never blank - handled by
  `AuthenticatedUserResolver.resolveAuditIdentity(auth, fallback)` and the matching
  `AuditLogService.record(..., performedByFallback)` overload.

## Error handling

A `@RestControllerAdvice` (`GlobalExceptionHandler`) maps each domain
"not found"/"duplicate"/"capacity exceeded" exception to the right HTTP status with
a JSON `ErrorResponse` body. `AccessDeniedException` is deliberately rethrown
(not converted to JSON) so Spring Security's own `AccessDeniedHandler` can forward
to the styled `/access-denied` page. `NoHandlerFoundException` and
`NoResourceFoundException` (an unmapped URL) are handled the same way, returning a
proper 404 - previously these fell through to the generic `Exception.class`
handler and came back as a misleading 500 "Something went wrong".

## Evolution log

Chronological record of what was built, in the order it happened. Add a new entry
here (don't rewrite old ones) whenever a session makes a meaningful change.

### Session 1: UI honesty pass
Removed dead/fake UI elements across every template: unbacked claims ("SSL
secured", fake compliance version numbers, a notification bell with no backing
feature, a "Forgot password?" link with no handler, href="#" placeholder links).
Added real empty states to every list page, consistent page titles, an inline SVG
favicon, and made the role subtitle in the top bar real (`CurrentUserAdvice`
started resolving an actual role label instead of a hardcoded "Cooperative
Administrator" string). No backend behavior changed.

### Session 2: Database-backed accounts, roles, operator self-registration
Replaced the hardcoded `InMemoryUserDetailsManager` (admin/admin123,
finance/finance123) with real `UserAccount` rows in PostgreSQL. Added the OPERATOR
role, a `/register` self-registration flow (new `UserAccount` + new `Operator` in
one transaction, reusing `OperatorService`'s capacity check), a
`CustomUserDetailsService` that logs in by email or phone, a
`RoleBasedAuthenticationSuccessHandler` to route OPERATOR-only accounts to a new
`/portal` page, and `SeedAccountsRunner` to create the first ADMIN/FINANCE_OFFICER
accounts from environment variables (never hardcoded). Added an admin-only
"Register me as an operator" feature and optional login-account fields on the
admin's operator-create form. Google OAuth2, the audit log, RabbitMQ, Redis and
the REST controllers were untouched.

### Session 3: Single-role enforcement, plate normalization, removing "register me"
Removed "Register me as an operator" completely (button, form, controller
methods, service method) after deciding an ADMIN/FINANCE_OFFICER account must
never also hold OPERATOR. Added `UserAccountService` as the one place that
enforces exactly one role per account (`MultipleRolesException` otherwise), and a
startup `RoleConflictRepairRunner` to fix any account left over from before this
rule existed (strips OPERATOR, unlinks the Operator row without deleting it).
Centralized plate number normalization and duplicate-detection into
`OperatorServiceImpl` (previously scattered and not normalized, so
differently-formatted duplicates slipped through); kept the DB unique constraint
as a backstop, translated to the same friendly message. Admin creating an
operator with a login, and the self-registration flow, both continued to work
through the same (now centralized) checks.

### Session 4: Due payment lifecycle
This is the session that added everything described above under "Due payment
lifecycle": the PENDING/PAID/OVERDUE status enum, `dueDate`/`reference`/`paidAt`/
`paymentMethod`/`issuedBy`, the unique (operator, type, dueDate) constraint, the
single-issue and bulk-issue flows (FINANCE_OFFICER only), the daily+startup
overdue job, and `getEffectiveStatus()` so the UI is never stale waiting on that
job. Replaced the old `datePaid`/free-text `status` fields; the physical
`date_paid` column is left in the table (unmapped) specifically so
`LegacyDuePaymentMigrationRunner` can read it directly via JDBC and backfill
`status`/`dueDate`/`paidAt` for rows that pre-date these columns, bypassing
Hibernate entirely for that one operation (loading an un-migrated row through the
JPA entity would throw, since the old status values like "Paid"/"Pending" do not
match the new enum's constant names). Also fixed two things found only by running
the app for real:
- Hibernate's `apply_to_ddl` was deriving `NOT NULL` DDL straight from the
  `@NotNull` bean-validation annotation on the new `dueDate` column, which made
  `ddl-auto=update` try to `ALTER ... ADD COLUMN ... NOT NULL` on a non-empty
  table and fail outright. Fixed by setting
  `spring.jpa.properties.hibernate.validator.apply_to_ddl=false` - validation
  still applies at the application layer via `@Valid`, it just no longer leaks
  into DDL generation.
- The old `date_paid` column's original `NOT NULL` constraint was still sitting
  in the database after the entity stopped mapping it, so every new insert (which
  never sets that column) violated it. `LegacyDuePaymentMigrationRunner` now also
  runs `ALTER TABLE due_payment ALTER COLUMN date_paid DROP NOT NULL` before the
  backfill.
Also fixed a pre-existing, unrelated bug found while reading the exception
handling: any unmapped URL returned a JSON 500 "Something went wrong" instead of
a 404, because `NoHandlerFoundException`/`NoResourceFoundException` were being
swallowed by the generic `Exception.class` handler. Added specific handlers for
both so they return a proper 404.

### Session 5: Operator pay flow (P4)
Added the actual payment half of the due payment lifecycle: operators can now act on
their own dues from `/portal`, not just view a static profile.

- `DuePaymentStatus` gained `SUBMITTED` (online payment started, awaiting the
  operator's confirm step), `CASH_PENDING` (operator asked to pay cash, awaiting a
  finance officer to confirm it - that confirmation itself is P5), and `FAILED` (a
  simulated online payment that did not succeed, operator may retry). `DuePayment`
  gained `submittedAt`.
- Four new `DuePaymentService` methods - `initiateOnlinePayment`,
  `confirmOnlinePayment`, `cancelOnlinePayment`, `requestCashPayment` - all take the
  calling operator and throw `AccessDeniedException` if it does not own the due
  (prevents paying someone else's due) and `InvalidPaymentStateException` if the due
  is not in a payable state (PENDING/OVERDUE/FAILED only - prevents double-paying an
  already PAID/SUBMITTED/CASH_PENDING due). No real payment gateway exists, so
  `confirmOnlinePayment` simulates one: a `app.payments.simulated-failure-rate`
  property (default 0.0, i.e. never fails) decides PAID vs FAILED, letting tests
  force the FAILED path deterministically without flakiness.
- New `/portal/duepayments/{id}/pay` (start), `/pay/confirm` (GET shows a review
  page, POST resolves it), `/pay/cancel`, and `/request-cash` endpoints on
  `PortalWebController`, all under the existing `/portal/**` OPERATOR-only rule.
  `/portal` itself now lists the operator's dues with Pay Online / Pay Cash actions
  matching their state, plus a "Recent Payments" panel.
- "Recent Payments" reads the existing Mongo `PaymentEventLog` collection (no new
  entity) rather than persisting payment history separately - `DuePaymentEvent` and
  `PaymentEventLog` gained `reference`/`paymentMethod` fields so that collection
  carries what the UI needs, and four new event-type constants
  (`duepayment.submitted`/`.paid`/`.failed`/`.cashrequested`) so the panel can filter
  to actual payment outcomes rather than every due mutation. All of this flows
  through the existing RabbitMQ publish/consume path unchanged.
- Closed a latent gap found while wiring this up: `/api/duepayments/**` (the REST
  controller) had no role restriction at all - unlike its `/web/duepayments/**`
  equivalent, any authenticated user including OPERATOR could hit full CRUD. Added
  the same `@PreAuthorize` rules (ADMIN/FINANCE_OFFICER read, FINANCE_OFFICER
  write) the web controller already had.
- The finance "issue/edit" form's status dropdown only listed PENDING/PAID/OVERDUE;
  left as-is it would have silently reset a SUBMITTED/CASH_PENDING/FAILED due back
  to PENDING if a finance officer opened and saved it without touching status
  (Thymeleaf selects an unmatched value to nothing, browser defaults to the first
  option). Extended it, and the due-payments list's status filter/badge colors, to
  cover all six statuses.

## Running it / testing it

See `README.md` for Docker setup and `.env` layout (that part has not changed).
Seed credentials for a throwaway test run should be passed as process environment
variables to that one `java -jar ...` invocation, never written into the real
`.env` or printed. Tests run against the real local Postgres/Mongo/Redis/RabbitMQ
containers (no embedded/test-double databases in this project) - start the
containers before running `mvn test` or `mvn clean install`.
