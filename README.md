# Workflow Management API

A backend API for managing users, projects, tasks, task assignments, and a task-approval workflow, built with Spring Boot. This is the foundation for a 4-week backend engineering project covering architecture, security, asynchronous/integration processing, and performance/production readiness; this repository currently covers Week 1.

## Contents

- [Status](#status)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Domain Model](#domain-model)
- [API Endpoints](#api-endpoints)
- [Pagination, sorting, and filtering](#pagination-sorting-and-filtering)
- [Validation](#validation)
- [Error responses](#error-responses)
- [Transactions and concurrency](#transactions-and-concurrency)
- [API documentation (Swagger / OpenAPI)](#api-documentation-swagger--openapi)
- [Documentation](#documentation)

## Status

Week 1 complete: layered architecture, the core domain model, 26 REST endpoints across five resources, validation (including a custom DB-backed rule and a cross-field rule), centralized error handling, and optimistic locking on the two entities most exposed to concurrent writes. See `docs/progress.md` for the running log.

## Tech Stack

- Java 17
- Spring Boot 3.3
- Spring MVC (REST controllers)
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
  config/            application-level configuration (OpenAPI)
  controller/        REST controllers
  service/           service interfaces
  service/impl/      service implementations
  repository/        Spring Data JPA repositories
  entity/            JPA entities and enums
  dto/               request/response DTOs
  dto/validation/    custom Bean Validation annotations and validators
  exception/         custom exceptions and centralized error handling
```

## Getting Started

### Prerequisites

- JDK 17+
- Maven 3.9+
- PostgreSQL (optional for local dev — an in-memory H2 database is used by default)

### Run locally

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8080` with the `dev` profile active by default (H2 in-memory database, console at `/h2-console`). No environment variables are required for local dev.

To run against PostgreSQL, activate the `postgres` profile and set the connection details — there are no committed defaults for these, on purpose:

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
| POST   | `/api/v1/tasks/{taskId}/approvals` | Request approval on a task (`{requestedByUserId, comments}`) |
| PATCH  | `/api/v1/approvals/{id}/decision` | Approve, reject, or cancel a pending approval (`{decidedByUserId, status, comments}`) |
| GET    | `/api/v1/approvals` | List approvals, paginated, optional `?taskId=&status=` filters |
| GET    | `/api/v1/approvals/{id}` | Get an approval by id |

Only one `PENDING` approval is allowed per task at a time. Approving one moves the underlying task to `DONE`; rejecting one leaves the task's status untouched so work can continue.

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
- [`docs/progress.md`](docs/progress.md) — running log of what's been built
