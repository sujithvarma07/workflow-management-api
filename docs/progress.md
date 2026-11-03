# Progress Log

## Week 1 — Advanced API Architecture and Database Design (complete)

- [x] Layered Spring Boot architecture: `controller` -> `service`/`service/impl` -> `repository`, with `entity` and `dto`/`dto/validation` kept separate so persistence models never appear directly on the API surface.
- [x] REST API covering a business workflow of users, projects, tasks, assignments, and approvals — 26 endpoints across five resources (create, retrieve, update, delete, filtering, sorting, and pagination on every list endpoint). Full table in the README.
- [x] Normalized relational schema: surrogate primary keys throughout, unique constraints on `app_user.username`/`email`, `project.name`, and `project_member.(project_id, user_id)`, and foreign keys on every relationship. See `docs/erd.md`.
- [x] Entity relationships: one-to-many (`Project` -> `Task`, `Task`/`User` -> `Approval`), many-to-one (the reverse direction of each of those), and two many-to-many relationships implemented through attribute-carrying join entities (`ProjectMember` for `User`<->`Project`, `Assignment` for `User`<->`Task`).
- [x] DTO-based request/response models on every endpoint — no entity is ever serialized directly or accepted directly as a request body.
- [x] Advanced validation: standard Jakarta Bean Validation annotations throughout, plus one custom field-level constraint backed by a database lookup (`@UniqueUsername`) and one custom class-level cross-field constraint (`@ValidApprovalDecision`, requiring a comment when rejecting an approval).
- [x] Centralized exception handling via a single `@RestControllerAdvice` (`GlobalExceptionHandler`) producing one consistent JSON error shape across validation failures (400), not-found (404), conflicts and invalid state transitions (409), and optimistic-locking conflicts (409).
- [x] Transaction boundaries drawn at the service layer with `@Transactional`/`@Transactional(readOnly = true)`, using Spring's default `REQUIRED` propagation throughout; rollback relies on every custom exception being an unchecked `RuntimeException`. Full write-up in `docs/concurrency-design.md`.
- [x] Concurrency: identified the lost-update risk on `Project`, `Task`, and `Approval` (concurrent edits to the same row) and addressed it with `@Version`-based optimistic locking on all three, converted into a 409 response by `GlobalExceptionHandler`. Identified the duplicate-request risk on user/project creation, project membership, task assignment, and approval requests, and addressed each with an application-level existence check backed by a database-level unique constraint where the check alone wouldn't be race-proof (`project_member`).
- [x] API documentation: springdoc/Swagger UI enabled and reachable once the app is running, plus the full endpoint table and request/response shapes described in the README.

### Notes

- No authentication or authorization is implemented yet — that's explicitly Week 2 scope (JWT, role-based access control, ownership-based authorization). Every endpoint is open for now.
- Approving a task's approval request automatically transitions the task to `DONE` — this was a deliberate design choice to make the approval workflow actually gate task completion rather than just record a decision alongside it, and is documented in the README's Approvals section and in `ApprovalServiceImpl`.
- The `postgres` Spring profile requires `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`/`DB_PASSWORD` as environment variables with no defaults, consistent with not committing any credential to the repository — the `dev` profile (H2, used for local development by default) needs no environment variables at all.
- Testing (unit, integration, coverage) is Week 4 scope per the assignment and hasn't been built yet; Week 1 focused on getting the architecture, schema, and API surface right first.
