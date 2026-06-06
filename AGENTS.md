# P2P Lending Platform - AGENTS.md

This file is the Codex entrypoint for this repository.

For full project context, architecture, API flow, database notes, and operational commands, read `CLAUDE.md` first. Treat `CLAUDE.md` as the source of truth for domain details. If `CLAUDE.md` and this file ever disagree, follow `CLAUDE.md` and update this file.

## Project Summary

This is a P2P Lending microservices platform built with Java 21 and Spring Boot 3.3.5.

Services:

- `auth-service`: phone login/register, OTP, JWT RS256, eKYC
- `loan-service`: loan request lifecycle and investment offers
- `matching-service`: investor preference matching
- `cms-service`: CMS admin/auth service
- `notification-service`: Kafka-driven notification worker

Infrastructure:

- MySQL 8.0
- Redis 7
- Kafka + Zookeeper
- Nginx reverse proxy on host port `7080`
- Internal Nginx/reverse proxy and Docker network routing are used for service-to-service/API routing where configured.

Important project vocabulary:

- `CMS` means `Customer Manager Service`, not Content Management System.
- CMS admin users are stored in `cms_db.cms_admin_users`.
- Customer app users stay in `auth_db.users`; loan data stays in `loan_db.loan_requests`.
- CMS must not mirror customer/user or loan tables into `cms_db`.
- Services already call each other through internal APIs where needed; do not reintroduce DB mirroring to share data.

## Service-To-Service Calls

The backend is no longer purely event/mirror based. Services can call each other through internal HTTP APIs and internal Nginx/Docker network routing.

Current important internal flows:

- `cms-service` gets customer data from `auth-service` internal user APIs.
- `cms-service` gets loan data and sends loan review actions to `loan-service` internal loan APIs.
- Internal APIs must be protected with `INTERNAL_API_SECRET` / `X-Internal-Secret`.
- Prefer source-of-truth service APIs over copying tables between databases.
- Use service DNS names inside Docker, e.g. `http://auth-service:8081` and `http://loan-service:8082`, unless nginx/internal proxy config says otherwise.

## Error Handling Rules

- Do not hide business, validation, downstream service, or database/schema errors behind generic `Internal server error` messages.
- When a service calls another service, preserve the source service HTTP status and extract useful error text from `details[]`, `message`, `detail`, or `error`.
- Frontend clients must read both single-message errors and `details[]` arrays, then show the specific message returned by the API.
- Still log full server-side exceptions with `@Slf4j`; user-facing messages should be specific enough to troubleshoot UAT issues without requiring immediate server log access.

## Seed Data Rules

- Seed files may insert/update configuration data only.
- Never `TRUNCATE`, `DELETE`, drop, recreate, or reset business/user data from seed files, migrations, deploy scripts, local scripts, or manual SQL unless the user explicitly asks for a destructive reset and confirms a backup exists.
- Protected data includes `loan_requests`, `loan_offers`, `users`, KYC, payment, transaction, audit, notification, and customer/admin operational records.
- UAT/test deploys can run Flyway repeatable seeds; seeds must be idempotent and data-preserving, usually via `INSERT ... ON DUPLICATE KEY UPDATE`.

## Codex Working Rules

- Prefer the existing Spring Boot service structure and conventions.
- Keep changes scoped to the requested service or API flow.
- Do not delete or rewrite unrelated user changes.
- Use DTOs for request/response payloads; do not expose entities directly.
- Keep the `Controller -> Service -> Repository` layering.
- Use MapStruct where the project already uses it for mapping.
- Use Lombok `@Slf4j` for logging; do not use `System.out.println`.
- Use `@Enumerated(EnumType.STRING)` for status enums.
- Use UUID IDs with `VARCHAR(36)` and `@GeneratedValue(strategy = GenerationType.UUID)` unless an existing table explicitly differs.

## Required Entity/Table Fields

Every table must include these fields unless an existing legacy table clearly does not follow the rule:

- `isDeleted`: soft delete flag, Java `boolean`, SQL `TINYINT(1) NOT NULL DEFAULT 0`
- `createdAt`: `LocalDateTime`, `@CreationTimestamp`, SQL `DATETIME DEFAULT CURRENT_TIMESTAMP`
- `updatedAt`: `LocalDateTime`, `@UpdateTimestamp`, SQL `DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`

Never hard-delete business records. Use soft delete by setting `isDeleted = true`, and filter queries with `is_deleted = 0`.

## Transaction Rules

All service-layer methods that touch the database must be transactional:

- Pure reads: `@Transactional(readOnly = true)`
- Writes: `@Transactional`
- Read then write in one method: `@Transactional`
- DB-backed validation: `@Transactional(readOnly = true)`

Do not rely on `@Transactional` on private methods or self-invoked methods because Spring proxying will not apply.

## Auth And Registration Flow

Registration is phone-first:

1. `POST /api/auth/check-phone`
2. `POST /api/auth/register`
3. `POST /api/auth/register/verify`

Newly registered users:

- login with `phone`
- start with role `USER`
- start with `kyc_status = NONE`
- may both invest and raise capital
- have `email = null` and `full_name = null` until updated later

Password validation must match the frontend contract:

- at least one uppercase character
- at least one number
- at least one special character

Vietnamese phone numbers and CCCD numbers must be validated consistently with the mobile app.

## Loan Approval Flow

P2P lending loans must not go live immediately after CMS leadership approval:

1. Borrower submits a capital-raising request.
2. Appraiser proposes approved amount and annual interest rate.
3. Leadership approves the proposed/final terms.
4. `loan-service` sets status to `AWAITING_BORROWER_APPROVAL` and notifies the borrower with approved amount, interest rate, and term.
5. Borrower accepts the approved terms through `POST /api/loans/{id}/confirm`.
6. Only after borrower acceptance may the loan become `ACTIVE` and visible on the marketplace for investors.

Never change CMS approval to directly publish a loan to the marketplace unless the user explicitly changes the business flow.

## Mobile OTP UI Rules

All OTP screens in the VNFITE mobile app must use the same OTP entry pattern:

- Display OTP as separate digit boxes, one box per number.
- Do not use one long plain text input for OTP entry.
- Prefer the shared mobile OTP component/pattern when available.
- Keep OTP input numeric-only, fixed length, and visually consistent across registration, password reset, eKYC, biometric enable/disable, and device reset flows.
- OTPs must remain scoped to their feature/purpose and must not be reusable across unrelated flows.

## eKYC Flow

Authenticated eKYC has two steps:

1. `POST /api/auth/kyc/init`
2. `POST /api/auth/kyc/verify`

Mock OTP is `000000` when mock mode is enabled.

After successful eKYC in the mobile flow, the user is expected to log in again.

## Token Rules

Follow the current implementation and `CLAUDE.md` for token TTLs unless the user explicitly asks to change them.

Mobile banking-like behavior currently expected by the app:

- access token should be short-lived
- refresh token should be short-lived for test/UAT behavior
- if refresh token expires while app is in background, the app should show a custom VNFITE session-expired modal and require login again

## Build And Run

Common commands:

```bash
docker compose up -d
docker compose build auth-service
docker compose up -d auth-service
docker compose logs -f auth-service
docker compose down
```

Build a service JAR manually:

```bash
cd apps/api/auth-service
mvn clean package -DskipTests
```

## Test/UAT

Test server:

- Backend server: `42.113.122.119`
- Nginx/API domain: `https://api-uat.vnfite.com.vn`
- Health check: `GET /health`

Seed users in dev/test use password `Test@1234`; see `CLAUDE.md` for the full list.

## API Prefixes

- Auth: `/api/auth`
- Loans: `/api/loans`
- Matches: `/api/matches`
- CMS admin/auth: `/cms`

## Timezone

Use `Asia/Ho_Chi_Minh` consistently.

## Before Finishing Backend Changes

- Run the focused build/test command for touched services when feasible.
- If tests are not available or not run, say that clearly.
- Check `git status --short`.
- Do not commit or push unless the user asks for it.
- When changes are ready, provide the exact `git add`, `git commit`, and `git push` commands for the user to run.
