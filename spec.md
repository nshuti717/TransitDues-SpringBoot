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
  **PAID** once a finance officer confirms it, or back to **PENDING** if they
  reject it (Session 6/P5 - see "Finance collections and cash confirmation"
  below).

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

### Session 6: Finance collections and cash confirmation (P5)
Closed the loop CASH_PENDING left open in Session 5: finance can now see and act on
it, not just operators seeing "Awaiting finance confirmation" forever.

- `DuePaymentService` gained `confirmCashPayment`/`rejectCashPayment`
  (FINANCE_OFFICER-only, same `@PreAuthorize` placement as issue/bulk-issue/update
  on `DuePaymentWebController` - new endpoints `/web/duepayments/{id}/confirm-cash`
  and `/reject-cash`). Confirming sets PAID with a `CASH-XXXXXXXX` reference and
  records `confirmedBy` (new `DuePayment` field); rejecting returns the due to
  PENDING. Both reject anything not currently CASH_PENDING via the same
  `InvalidPaymentStateException` used for the operator-side payment actions.
- New `/web/finance` "Collections" page (new `finance` package:
  `CollectionsService` + `CollectionsSummary`, new `FinanceWebController`,
  `ADMIN`+`FINANCE_OFFICER` read access like the other finance-adjacent pages):
  total expected/collected/outstanding, a status breakdown, and a filterable due
  list (operator name or plate, stage, status, due-date range - all done
  in-memory over `findAllDuePayments()`, consistent with how the existing
  due-payments list already filters by status; dataset sizes here don't call for
  Spring Data Specifications). CASH_PENDING rows get inline Confirm/Reject
  buttons. The same two buttons were also added to the plain `/web/duepayments`
  list, since cash requests can land there too - both post to the same
  controller actions, with a hidden `redirectTo` field (whitelisted to exactly
  `"finance"` or the default) so confirming from either page returns to that page,
  without opening an open-redirect.
- `CollectionsSummary` is computed fresh on every request, deliberately not
  behind the `dashboardStats` Redis cache: finance watching this page while
  working a stack of cash requests expects each confirm/reject to be reflected
  immediately, not up to 60 seconds later.

### Session 7: OTP, email via RabbitMQ, and Google login for operators (P6)
Added a real email channel and backed two things with it: password reset, and
letting operators (not just staff) sign in with Google.

- **OTP** (`otp` package): `OtpVerification` (Postgres) stores only a BCrypt hash
  of a random 6-digit code, an `expiresAt`, a per-record `attempts`/`maxAttempts`,
  and `consumed`. `OtpService.generate(email, purpose)` invalidates (consumes) any
  earlier unconsumed code for that email+purpose before creating the new one,
  so only the latest code is ever valid; `verify(...)` enforces expiry, the
  attempt cap, and single-use, returning a result enum (`VERIFIED`/`INCORRECT`/
  `EXPIRED`/`TOO_MANY_ATTEMPTS`/`NOT_FOUND`) rather than throwing, so callers can
  show a specific message without the service dictating UI text.
- **Email via RabbitMQ** (`email` package): `EmailEventPublisher` publishes an
  `EmailEvent` to the existing `transitdues.events` exchange on a new routing-key
  pattern (`email.*`, parallel to the due-payment `duepayment.*` one); a new
  `transitdues.email.queue` binds to it. `EmailSendConsumer` is the consumer -
  unlike `NotificationConsumer`'s simulated log lines, this one is a real send via
  `JavaMailSender`, to Mailpit locally (new service in docker-compose.yml, UI at
  http://localhost:8025) so nothing is sent to a real inbox in dev.
  `spring-boot-starter-mail` added to pom.xml for this.
- **Real "Forgot password?" flow** (`passwordreset` package,
  `PasswordResetWebController`, public `/forgot-password` -> `/reset-password`):
  replaces the fake link removed in Session 1 - it now does something.
  `requestReset` always behaves identically whether or not the email belongs to
  an account (no account-enumeration oracle); `resetPassword` collapses
  incorrect/expired/not-found into one generic message for the same reason, only
  "too many attempts" gets a distinct message. A successful reset goes through
  `UserAccountService.save(...)`, the one sanctioned UserAccount write path.
- **Google login for operators**: `OAuth2UserRoleMapper` (still a
  `GrantedAuthoritiesMapper`, same wiring as before) now resolves a Google
  login's role in three steps instead of one - an email matching an existing
  `UserAccount` uses that account's own DB role(s); otherwise the
  `app.roles.admin-emails`/`finance-emails` lists are checked exactly as before;
  otherwise a **restricted OPERATOR-only account is auto-provisioned** (no linked
  `Operator`, a random BCrypt-encoded password that can never be typed in via the
  login form - the account is Google-login-only) so the person lands on
  `/portal` instead of a dead end. A Google login can still never grant ADMIN or
  FINANCE_OFFICER through auto-provisioning - only an existing DB account or the
  configured email lists can.
- `.env.example` now documents `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` (it
  referenced them in `application.properties` already but never listed them) and
  the new `MAIL_*`/`OTP_*` variables. `app.*.client-id`/`client-secret` stay as
  bare `${GOOGLE_CLIENT_ID}` with **no** default: Spring Boot's OAuth2
  autoconfiguration treats a genuinely-unresolved property as "no client
  registered" and skips it gracefully, but an explicit empty-string default
  (`${GOOGLE_CLIENT_ID:}`) is bound as a present-but-blank value and fails
  startup with "Client id of registration 'google' must not be empty" - this was
  tried and reverted during this session once it broke `contextLoads`.
- Unrelated infra fix found while running the full suite: `mvn test` was
  intermittently failing every Mockito-based test with "Could not self-attach to
  current VM using external process" (a Windows JVM self-attach restriction, not
  caused by any app code). Fixed by adding
  `-Djdk.attach.allowAttachSelf=true` to the surefire `argLine` in `pom.xml`.

### Session 8: Documentation and test hardening (P7)
- Added `docs/DOCUMENTATION.md`: the formal course-deliverable write-up (problem
  statement, objectives, scope, functional/non-functional requirements, user
  stories with acceptance criteria, architecture and ER diagrams in Mermaid,
  SDLC explanation, RBAC/auth/messaging/data-store explanations, testing plan,
  deployment instructions, known limitations). Deliberately does not duplicate
  `README.md` (setup + requirement mapping) or this file (the dated build log) -
  it answers the "why/how as a whole" questions neither of those is organized to
  answer.
- Added validation-error test coverage for the P6 password-reset forms
  (`PasswordResetWebControllerTest`: malformed email, malformed OTP code,
  mismatched passwords, too-short password). Writing
  `forgotPasswordRejectsAMalformedEmail` caught a real bug: `forgot-password.html`
  evaluates `${!submitted}`, but `PasswordResetWebController.requestReset`'s
  validation-failure branch never set `submitted` on the model, so a validation
  error threw a SpEL evaluation exception instead of showing the field error -
  fixed by setting `submitted=false` on that branch, same as the GET handler
  already did.
- Full suite: 70 tests, all passing, run against the real local containers (not
  mocked) - see `docs/DOCUMENTATION.md`'s "Testing plan" for the breakdown by
  category.

### Session 9: Email OTP verification during operator registration
Self-registration (`/register`) used to grant immediate `/portal` access. Now a
new account is `PENDING_VERIFICATION` until its email is confirmed.

- **`AccountStatus`** (`PENDING_VERIFICATION`, `ACTIVE`) - new field on
  `UserAccount`, defaulting to `ACTIVE` in Java so every creation path except
  self-registration (seeded admin/finance, admin-created operator logins, Google
  auto-provisioning) is unaffected without any change at those call sites. Not
  `nullable=false` at the JPA level (same reasoning as `DuePayment.dueDate`
  back in Session 4: `ddl-auto=update` can't add a NOT NULL column to a
  non-empty table) - `AccountStatusBackfillRunner` backfills existing NULL rows
  to ACTIVE at startup, same pattern as `LegacyDuePaymentMigrationRunner`.
- **`CustomUserDetailsService`** now reports an account `disabled` if
  `!enabled` **or** `status == PENDING_VERIFICATION` - one extra condition on
  the exact mechanism Spring Security already used for admin-disabled accounts,
  rather than a second, parallel gate. This alone is what makes a pending
  account unable to reach `/portal`, `/web/**`, or the REST API: it can never
  establish a session via form login in the first place. Google login and
  password reset are untouched (different code paths); this status is deliberate
  scope-limited to self-registration only.
- **OTP reused as-is**: new `OtpPurpose.REGISTRATION_VERIFY` constant, zero
  other changes to `OtpService` - same hash-only storage, 10-minute expiry,
  5-attempt cap, auto-invalidation of earlier codes. New
  `OtpService.secondsUntilResendAllowed(email, purpose, cooldownSeconds)`
  (reads the newest row's `createdAt` regardless of consumed state) backs a
  **server-side** 60-second resend cooldown - the resend button's client-side
  disable is just UX, not the actual enforcement; a replayed POST is still
  refused with "please wait N seconds."
- **New `verification` package** (`VerifyAccountForm`,
  `AccountVerificationOutcome`, `AccountVerificationService`) mirrors
  `passwordreset`'s shape exactly: generic incorrect/expired messaging, no
  account-enumeration on resend (an unknown or already-ACTIVE email is a silent
  no-op that still looks like success). `RegistrationService.registerOperator`
  calls `AccountVerificationService.sendVerificationCode(...)` after creating
  the PENDING_VERIFICATION account; `RegisterWebController` redirects to
  `/verify-account?email=...` instead of `/login?registered`.
- **Programmatic login on success**: `AccountVerificationWebController` loads
  the just-verified account's `UserDetails` via the same
  `CustomUserDetailsService` a normal login uses, puts it on the
  `SecurityContext`, and persists it via `HttpSessionSecurityContextRepository`
  - the standard Spring-Security-documented way to authenticate someone outside
  the login form - then redirects to `/portal?verified`, which shows "Your
  account has been verified successfully."
- **Bug found and fixed while testing**: `AccountVerificationFlowTest`'s first
  run failed with a Postgres `otp_verification_purpose_check` constraint
  violation on the very first `REGISTRATION_VERIFY` insert. Hibernate's
  `ddl-auto=update` had generated that CHECK constraint back when `OtpPurpose`
  only had `PASSWORD_RESET` (Session 7) and never widens it for a later enum
  addition - only affects a database that already had the table before this
  session, never a fresh one. Fixed by `OtpPurposeConstraintMigrationRunner`
  (drops the stale constraint at startup; the enum is still fully enforced at
  the application layer by `@Enumerated(STRING)` regardless).
- Full suite: 88 tests, all passing. Manually verified end-to-end against the
  real stack: registered an operator, read the real "Verify your TransitDues
  account" email out of Mailpit, confirmed a wrong code shows the generic error,
  confirmed the resend cooldown is enforced server-side (not just by the
  disabled button), and confirmed the correct code activates the account, logs
  it in, and lands on `/portal?verified` with dues/payment history intact.

## Running it / testing it

See `README.md` for Docker setup and `.env` layout (that part has not changed).
Seed credentials for a throwaway test run should be passed as process environment
variables to that one `java -jar ...` invocation, never written into the real
`.env` or printed. Tests run against the real local Postgres/Mongo/Redis/RabbitMQ
containers (no embedded/test-double databases in this project) - start the
containers before running `mvn test` or `mvn clean install`.
