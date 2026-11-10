# Workflow Management API

A backend API for managing users, projects, tasks, task assignments, and a task-approval workflow, built with Spring Boot. This is the foundation for a 4-week backend engineering project covering architecture, security, asynchronous/integration processing, and performance/production readiness; this repository currently covers Week 1.

## Contents

- [Status](#status)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Domain Model](#domain-model)
- [Authentication and authorization](#authentication-and-authorization)
- [API Endpoints](#api-endpoints)
- [Pagination, sorting, and filtering](#pagination-sorting-and-filtering)
- [Validation](#validation)
- [Idempotency](#idempotency)
- [Audit logging](#audit-logging)
- [Error responses](#error-responses)
- [Transactions and concurrency](#transactions-and-concurrency)
- [API documentation (Swagger / OpenAPI)](#api-documentation-swagger--openapi)
- [Documentation](#documentation)

## Status

Week 1 and Week 2 complete. Week 1 built the layered architecture, the core domain model, 26 REST endpoints across five resources, validation, centralized error handling, and optimistic locking. Week 2 added JWT authentication, role- and ownership-based authorization, the full account lifecycle (registration, activation, suspension, password reset), audit logging, and idempotency support for critical write operations. See `docs/progress.md` for the running log.

## Tech Stack

- Java 17
- Spring Boot 3.3
- Spring MVC (REST controllers)
- Spring Security (JWT, method security)
- Spring Data JPA / Hibernate
- PostgreSQL (H2 for local dev)
- Jakarta Bean Validation
- Maven
- Lombok

## Architecture

A standard layered structure: controller, then service, then repository, with DTOs on both the request and response side so the JPA entities never appear directly in the API. See [`docs/erd.md`](docs/erd.md) for the full entity relationship diagram and [`docs/concurrency-design.md`](docs/concurrency-design.md) for how transactions are scoped and which concurrency problems are handled where.

## Project Structure

```
src/main/java/com/relic/backend/
  config/            application-level configuration (OpenAPI, idempotency filter)
  controller/        REST controllers
  service/           service interfaces
  service/impl/      service implementations
  repository/        Spring Data JPA repositories
  entity/            JPA entities and enums
  dto/               request/response DTOs
  dto/validation/    custom Bean Validation annotations and validators
  exception/         custom exceptions and centralized error handling
  security/          JWT issuing/validation, Spring Security config, ownership-based authorization checks
  audit/             the single place that writes to the audit log
```

## Getting Started

### Prerequisites

- JDK 17+
- Maven 3.9+
- PostgreSQL (optional for local dev — an in-memory H2 database is used by default)

### Run locally

As of Week 2, a few environment variables are required at startup regardless of profile — there are no committed defaults, on purpose (see `docs/security-report.md`). `scripts/set-local-env.sh` is a gitignored convenience script with placeholder values you can edit and source:

```bash
source scripts/set-local-env.sh
mvn spring-boot:run
```

Or export them directly:

```bash
export JWT_SECRET=<a-random-string-at-least-32-bytes-long>
export ADMIN_BOOTSTRAP_USERNAME=admin
export ADMIN_BOOTSTRAP_EMAIL=admin@example.com
export ADMIN_BOOTSTRAP_PASSWORD=<choose-your-own>
mvn spring-boot:run
```

The app starts on `http://localhost:8080` with the `dev` profile active by default (H2 in-memory database, console at `/h2-console`). On first startup, one `ADMIN` account is created automatically from the `ADMIN_BOOTSTRAP_*` variables (see `AdminBootstrapSeeder`) — that's the only way to get an initial account, since self-registration always creates a `DEVELOPER`.

To run against PostgreSQL, activate the `postgres` profile and also set the connection details:

```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=workflow_db
export DB_USERNAME=<your-db-username>
export DB_PASSWORD=<your-db-password>
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## Domain Model

Five entities: `User`, `Project`, `ProjectMember`, `Task`, `Assignment`, and `Approval`.

- A `Project` has many `Task`s (one-to-many).
- `User` and `Project` are connected many-to-many through `ProjectMember`, which carries the member's role within that specific project (`OWNER` / `CONTRIBUTOR` / `VIEWER`).
- `User` and `Task` are connected many-to-many through `Assignment`, which keeps one row per assignment event (with `assignedAt`/`unassignedAt`/`active`) rather than a single current-assignee column, so assignment history is preserved.
- `Approval` belongs to a `Task` (one-to-many: a task can accumulate multiple approval requests over its life) and references two different `User`s — the one who requested the approval and the one who decided it.

Full diagram and constraint details: [`docs/erd.md`](docs/erd.md).

## Authentication and authorization

Every endpoint requires a valid JWT access token except the auth endpoints themselves, Swagger/OpenAPI, and the actuator health check (`SecurityConfig.PUBLIC_ENDPOINTS`).

### Account lifecycle

| Method | Endpoint | Description |
|--------|----------|--------------|
| POST   | `/api/v1/auth/register` | Self-register a new account — always created as `DEVELOPER`, `PENDING_ACTIVATION` |
| POST   | `/api/v1/auth/activate` | Activate an account with the token from registration |
| POST   | `/api/v1/auth/login` | Exchange username/password for an access token and a refresh token |
| POST   | `/api/v1/auth/refresh` | Exchange a refresh token for a new access token (rotates the refresh token) |
| POST   | `/api/v1/auth/logout` | Revoke a refresh token |
| POST   | `/api/v1/auth/forgot-password` | Request a password reset token |
| POST   | `/api/v1/auth/reset-password` | Reset a password with a valid reset token |
| PATCH  | `/api/v1/users/{id}/status` | ADMIN-only: suspend or reactivate a user |
| GET    | `/api/v1/users/me` | Any authenticated user: view their own profile |

`RegisterResponse.activationToken` and `ForgotPasswordResponse.resetToken` are returned directly in the response — this project has no email-sending infrastructure to deliver them through. In a real deployment these would be emailed, never returned by the API (see `docs/security-report.md`).

### Tokens

Access tokens are short-lived JWTs (`ACCESS_TOKEN_EXPIRATION_MINUTES`, default 15 minutes), signed with `JWT_SECRET`. Refresh tokens are opaque, long-lived (`REFRESH_TOKEN_EXPIRATION_DAYS`, default 7 days), stored only as a SHA-256 hash, and rotated on every use — a stolen refresh token stops working the moment it's used once, and logging out, resetting a password, or being suspended all revoke it immediately rather than waiting for it to expire naturally.

### Roles and ownership

Three roles: `ADMIN`, `MANAGER`, `DEVELOPER`. Beyond plain role checks (`@PreAuthorize("hasRole(...)")`), two rules depend on the specific record, not just the role, backed by `AuthorizationService` (the `authz` bean):

- A `MANAGER` can only update/delete a project, or manage its membership, if they are that specific project's `OWNER` member (set automatically on the project they create).
- A `DEVELOPER` can only change the status of a task, or request its approval, if they are actively assigned to that specific task.

Full breakdown: [`docs/role-permission-matrix.md`](docs/role-permission-matrix.md). Threat model and mitigations: [`docs/security-report.md`](docs/security-report.md).

## API Endpoints

### Users

| Method | Endpoint | Description |
|--------|----------|--------------|
| POST   | `/api/v1/users` | Create a user |
| GET    | `/api/v1/users` | List users, paginated (`?page=&size=&sort=`), optional `?role=` filter |
| GET    | `/api/v1/users/{id}` | Get a user by id |
| PUT    | `/api/v1/users/{id}` | Update a user's email, full name, or role |
| DELETE | `/api/v1/users/{id}` | Delete a user |

### Projects

| Method | Endpoint | Description |
|--------|----------|--------------|
| POST   | `/api/v1/projects` | Create a project |
| GET    | `/api/v1/projects` | List projects, paginated, optional `?status=` filter |
| GET    | `/api/v1/projects/{id}` | Get a project by id |
| PUT    | `/api/v1/projects/{id}` | Update a project |
| DELETE | `/api/v1/projects/{id}` | Delete a project |
| POST   | `/api/v1/projects/{id}/members` | Add a member to a project (`{userId, roleInProject}`) |
| GET    | `/api/v1/projects/{id}/members` | List a project's members |
| DELETE | `/api/v1/projects/{id}/members/{userId}` | Remove a member from a project |

### Tasks

| Method | Endpoint | Description |
|--------|----------|--------------|
| POST   | `/api/v1/tasks` | Create a task under a project |
| GET    | `/api/v1/tasks` | List tasks, paginated, optional `?projectId=&status=` filters |
| GET    | `/api/v1/tasks/{id}` | Get a task by id |
| PUT    | `/api/v1/tasks/{id}` | Update a task |
| PATCH  | `/api/v1/tasks/{id}/status` | Transition a task's status, enforcing the allowed transitions below |
| DELETE | `/api/v1/tasks/{id}` | Delete a task |

#### Task status transitions

```
TODO        -> IN_PROGRESS, BLOCKED, CANCELLED
IN_PROGRESS -> BLOCKED, DONE, CANCELLED
BLOCKED     -> IN_PROGRESS, CANCELLED
DONE        -> (final state)
CANCELLED   -> (final state)
```

An unlisted transition is rejected with a 409 naming both states. A task is also moved to `DONE` automatically when an approval requested against it is `APPROVED` (see Approvals below) — a task does not need its status set manually at that point.

### Assignments

| Method | Endpoint | Description |
|--------|----------|--------------|
| POST   | `/api/v1/tasks/{taskId}/assignments` | Assign a user to a task (`{userId}`) |
| GET    | `/api/v1/tasks/{taskId}/assignments` | List the full assignment history for a task |
| DELETE | `/api/v1/assignments/{id}` | Unassign — marks the assignment inactive and records when |

### Approvals

| Method | Endpoint | Description |
|--------|----------|--------------|
| POST   | `/api/v1/tasks/{taskId}/approvals` | Request approval on a task (`{comments}`) — the requester is the authenticated user, not a client-supplied id |
| PATCH  | `/api/v1/approvals/{id}/decision` | Approve, reject, or cancel a pending approval (`{status, comments}`) — the decider is the authenticated user |
| GET    | `/api/v1/approvals` | List approvals, paginated, optional `?taskId=&status=` filters |
| GET    | `/api/v1/approvals/{id}` | Get an approval by id |

Only one `PENDING` approval is allowed per task at a time. Approving one moves the underlying task to `DONE`; rejecting one leaves the task's status untouched so work can continue. Whoever decides an approval can never be the same person who requested it, regardless of role — self-approval is rejected with a 409.

## Pagination, sorting, and filtering

All four list endpoints (`GET /api/v1/users`, `/projects`, `/tasks`, `/approvals`) return a Spring Data `Page` using the standard query parameters:

- `page` — zero-indexed page number (default `0`)
- `size` — page size (default `20`)
- `sort` — `property,direction`, repeatable for multi-field sort (default `id,asc`; `requestedAt,desc` for approvals, so the newest requests show first)

Each also supports the optional filters listed in its table above (e.g. `GET /api/v1/tasks?projectId=3&status=IN_PROGRESS&sort=dueDate,asc`). The response body includes `content` alongside `totalElements`, `totalPages`, `number`, `size`, and `first`/`last` flags.

## Validation

Every write endpoint validates its request body with Jakarta Bean Validation (`@Valid`), including two rules beyond the standard annotations:

- **`@UniqueUsername`** (`dto/validation`) — a field-level custom constraint that checks the database for an existing username before a user is created, so a duplicate username is rejected as a 400 validation error (with a field-level message) rather than only being caught later as a 409 at the service layer.
- **`@ValidApprovalDecision`** (`dto/validation`) — a class-level, cross-field constraint on `ApprovalDecisionRequest`: if `status` is `REJECTED`, `comments` must be non-blank, so a rejection is never recorded without a reason attached.

## Idempotency

`POST /api/v1/tasks`, `POST /api/v1/projects`, and `POST /api/v1/auth/register` accept an optional `Idempotency-Key` header. Sending the same key on a retried request (after a timeout or dropped connection, for example) returns the original response again rather than creating a second record. A request without the header behaves exactly as before — the header is opt-in, not required. See `IdempotencyFilter` for the implementation and its documented in-process (single-instance) limitation.

## Audit logging

Security- and business-critical actions are recorded to a single `audit_log` table via `AuditService` — the only class in this codebase that writes to it, which is what makes "never log a secret" an enforceable rule rather than a convention every call site has to remember. Each entry captures who (`username`), what (`action`), on which record (`resourceType`/`resourceId`), whether it succeeded, and a short, non-sensitive note. Currently covered: registration, activation, login (including failed attempts), token refresh, logout, password reset requests and completions, user creation and status changes, project creation, task status changes, and approval requests/decisions (including blocked self-approval attempts).

## Error responses

All error responses (validation failures, not-found, conflicts, optimistic-locking conflicts, unexpected exceptions) share one JSON shape, produced by a single `@RestControllerAdvice` (`GlobalExceptionHandler`):

```json
{
  "timestamp": "2026-11-03T10:15:30Z",
  "status": 409,
  "error": "Conflict",
  "message": "Task 12 already has a pending approval request",
  "path": "/api/v1/tasks/12/approvals",
  "fieldErrors": null
}
```

`fieldErrors` is populated (as a list of `{field, message}`) for validation failures, including a synthetic `"object"` field for class-level constraint violations like `@ValidApprovalDecision`.

## Transactions and concurrency

- `Project`, `Task`, and `Approval` all carry a `@Version` column; a write that loses the race against a concurrent update fails with a 409 rather than silently overwriting the other change.
- Every service write method runs in a single `@Transactional` boundary, so a multi-step operation like deciding an approval (which can also update the task it belongs to) commits or rolls back as one unit.
- Full design rationale, propagation/rollback behavior, and the specific concurrency problems this addresses: [`docs/concurrency-design.md`](docs/concurrency-design.md).

## API documentation (Swagger / OpenAPI)

Interactive API docs are available once the app is running:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Raw OpenAPI spec: `http://localhost:8080/v3/api-docs`

## Documentation

- [`docs/erd.md`](docs/erd.md) — entity relationship diagram, relationships, keys, and indexing notes
- [`docs/concurrency-design.md`](docs/concurrency-design.md) — transaction boundaries, propagation/rollback behavior, and concurrency handling
- [`docs/role-permission-matrix.md`](docs/role-permission-matrix.md) — what each role can do, including the ownership-based rules
- [`docs/security-report.md`](docs/security-report.md) — threat model, mitigations, and known limitations
- [`docs/progress.md`](docs/progress.md) — running log of what's been built
