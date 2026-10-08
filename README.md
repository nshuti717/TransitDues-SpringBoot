# TransitDues

TransitDues is a Spring Boot cooperative ledger for Kigali's moto-taxi stages. It
tracks stages (physical taxi ranks), the operators assigned to each stage, and the
daily/quarterly dues those operators pay. It exposes both a REST API and a
server-rendered Thymeleaf web application, backed by PostgreSQL, MongoDB, Redis and
RabbitMQ, all running locally in Docker.

## Architecture

```
                         +-------------------+
                         |      Browser      |
                         +-------------------+
                            |            |
                     Thymeleaf UI     REST API
                      (/web/**)        (/api/**)
                            |            |
                            v            v
                      +---------------------------+
                      |   Spring MVC Controllers   |
                      +---------------------------+
                                   |
                                   v
                      +---------------------------+
                      |     Service Layer          |
                      |  Stage / Operator /         |
                      |  DuePayment / DashboardStats |
                      +---------------------------+
                        |        |        |       |
                        v        v        v       v
                 +----------+ +-------+ +-----+ +---------+
                 | Postgres | | Mongo | |Redis| |RabbitMQ |
                 | (JPA)    | (audit | |cache| |(events) |
                 |          | & event| |     | |         |
                 |          | log)   | |     | |         |
                 +----------+ +-------+ +-----+ +---------+
```

Every mutation to a Stage, Operator or DuePayment goes through the service layer,
regardless of whether it arrived through the REST controllers or the Thymeleaf web
controllers. The service layer writes the primary record to PostgreSQL, records an
audit entry in MongoDB, evicts the Redis dashboard cache, and (for due payments)
publishes an event to RabbitMQ that a separate consumer turns into another MongoDB
audit record and a simulated notification.

## Course requirements mapping

| # | Requirement | Where it is implemented |
|---|---|---|
| 1 | CRUD REST API with validation and business logic | `stage/`, `operator/`, `duepayment/` packages: `*Controller`, `*ServiceImpl`, `*Repository`, Jakarta Validation annotations on the domain entities |
| 2 | Server-rendered web UI (Thymeleaf) | `webcontroller/` (`StageWebController`, `OperatorWebController`, `DuePaymentWebController`, `DashboardWebController`) and `src/main/resources/templates/` |
| 3 | Authentication and role-based access control | `config/SecurityConfig.java` (form login, in-memory admin/finance users, `authorizeHttpRequests` rules per role), `webcontroller/AuthWebController.java`, `templates/login.html`, `templates/access-denied.html` |
| 4 | Third-party login (OAuth2/OIDC) | `config/OAuth2UserRoleMapper.java` (Google login via Spring Security OAuth2 client: an existing account's own DB role, then `OAUTH_ADMIN_EMAILS` / `OAUTH_FINANCE_EMAILS`, then auto-provisioning a restricted OPERATOR account - never ADMIN/FINANCE_OFFICER automatically) |
| 5 | NoSQL database integration | `audit/` package (`AuditLog`, `PaymentEventLog` MongoDB documents, `@Indexed` fields), `templates/audit-log.html` |
| 6 | Asynchronous messaging | `messaging/` package (`RabbitConfig`, `DuePaymentEventPublisher`, `PaymentAuditConsumer`, `NotificationConsumer`) and `email/` package (`EmailEventPublisher`, `EmailSendConsumer`) using RabbitMQ |
| 7 | Caching | `config/CacheConfig.java` (Redis-backed `dashboardStats` cache, 60s TTL), `dashboard/DashboardStatsService.java`, `@CacheEvict` on every create/update/delete |
| 8 | Relational database with explicit indexes and constraints | `stage/domain/Stage.java`, `operator/domain/Operator.java`, `duepayment/domain/DuePayment.java` (`@Table(indexes = ...)`, unique constraints, foreign keys) |
| 9 | Containerized infrastructure | `docker-compose.yml` (PostgreSQL, MongoDB, Redis, RabbitMQ, all bound to `127.0.0.1`), `.env.example` |

## How to run it

1. Install Docker Desktop and make sure it is running.
2. Copy `.env.example` to `.env` in the project root and fill in real values for every
   variable (database passwords, etc). `.env` is gitignored and must never be committed.
3. Start the infrastructure containers:

   ```
   docker compose up -d
   ```

   This starts PostgreSQL (`127.0.0.1:5433`), MongoDB (`127.0.0.1:27017`), Redis
   (`127.0.0.1:6379`), RabbitMQ (`127.0.0.1:5672`, management UI on `15672`) and
   Mailpit (SMTP on `127.0.0.1:1025`, web UI on `127.0.0.1:8025` - every email the
   app sends, e.g. a password-reset OTP, shows up there instead of a real inbox),
   all reachable only from your own machine.
4. Set up Google OAuth2 (optional, only needed to test Google login): create an OAuth
   client in the Google Cloud Console, then set `GOOGLE_CLIENT_ID` and
   `GOOGLE_CLIENT_SECRET` as environment variables for the process running the app (for
   example, in IntelliJ's Run Configuration "Environment variables" field). These are
   not read from `.env`.
5. Open the project in IntelliJ and run `TransitDuesSpringBootApplication`. The app
   starts on port 8080 by default and connects to the containers started in step 3.
6. Sign in at `/login` with the seeded accounts (`admin` / `admin123`,
   `finance` / `finance123`), or with a Google account whose email is listed in
   `OAUTH_ADMIN_EMAILS` or `OAUTH_FINANCE_EMAILS`.

## Roles and permissions

| Role | Granted to | Can access |
|---|---|---|
| `ADMIN` | the seeded `admin` user, or a Google login whose email is in `OAUTH_ADMIN_EMAILS` | Dashboard, Stages, Operators, Due Payments, Audit Log (full read/write on stages and operators) |
| `FINANCE_OFFICER` | the seeded `finance` user, or a Google login whose email is in `OAUTH_FINANCE_EMAILS` | Dashboard, Stages, Operators (read-only), Due Payments (full read/write), Collections |
| `OPERATOR` | self-registration at `/register` (email verification via OTP required before first sign-in - see below), admin creating an operator with a login, or a Google login matching neither list above (auto-provisioned, restricted, no verification needed since Google already verified the email) | `/portal` only |

### Configuring Google role emails

`OAUTH_ADMIN_EMAILS` and `OAUTH_FINANCE_EMAILS` are comma-separated lists of email
addresses, set in `.env` alongside the database credentials:

```
OAUTH_ADMIN_EMAILS=someone@example.com,another@example.com
OAUTH_FINANCE_EMAILS=finance.person@example.com
```

`OAuth2UserRoleMapper` reads these through `app.roles.admin-emails` /
`app.roles.finance-emails` in `application.properties`. On a Google login it checks, in
order: (1) does an account with this email already exist in the database - if so, use
*that* account's own role(s), whatever they are; (2) otherwise, does the email match
`OAUTH_ADMIN_EMAILS` or `OAUTH_FINANCE_EMAILS` - if so, grant `ROLE_ADMIN` /
`ROLE_FINANCE_OFFICER` (transient, no account is created); (3) otherwise, auto-provision
a new, restricted `UserAccount` with only `ROLE_OPERATOR` (no linked `Operator` profile,
a password that can never be used to form-login) so the person lands on `/portal`. A
Google login can never grant ADMIN or FINANCE_OFFICER through step 3 - only an existing
database account or the two email lists can.

## Stopping the containers

```
docker compose stop
```

This stops the containers without deleting their data volumes, so the next
`docker compose up -d` picks up right where it left off. Do not run
`docker compose down -v`: that deletes the named volumes and permanently erases the
PostgreSQL, MongoDB, Redis and RabbitMQ data.
