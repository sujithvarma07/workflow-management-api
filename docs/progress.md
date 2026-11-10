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

## Week 2 — Security, Authentication, and Business Logic (complete)

- [x] JWT-based authentication (`JwtService`, `JwtAuthenticationFilter`) — short-lived signed access tokens, validated on every request via a filter placed before Spring Security's own authentication filter.
- [x] Role-based access control for `ADMIN`, `MANAGER`, `DEVELOPER`, enforced with `@PreAuthorize`/`@EnableMethodSecurity` on every state-changing controller method.
- [x] Secure refresh-token mechanism (`RefreshTokenService`): opaque (not JWT) so it can actually be revoked, stored only as a SHA-256 hash, rotated on every use, with explicit expiration and revocation (on logout, password reset, and account suspension).
- [x] Password hashing via BCrypt (`PasswordEncoderConfig`), adaptive and self-salting.
- [x] Full account lifecycle: self-registration (always `DEVELOPER`, `PENDING_ACTIVATION`) with a separate activation step, admin-created accounts (already `ACTIVE`), suspension/reactivation (`PATCH /users/{id}/status`), and password reset (request + confirm, both token-based).
- [x] Ownership-based authorization beyond plain role checks (`AuthorizationService`, the `authz` bean): a `MANAGER` can only manage a project they own; a `DEVELOPER` can only change the status of, or request approval on, a task they're actively assigned to.
- [x] Audit logging (`AuditService`, `AuditLog`) — a single write path for every auditable action, covering auth events, user status changes, project creation, task status changes, and approval requests/decisions, with timestamp, actor, affected resource, and outcome on every row.
- [x] Idempotency for critical create operations (`IdempotencyFilter`) via an optional `Idempotency-Key` header on task/project/registration POSTs, so a retried request after a timeout doesn't create a duplicate.
- [x] Approval workflow hardened: self-approval (deciding your own request) is now blocked as an explicit business rule, independent of role.
- [x] Fixed a real broken-access-control gap left over from Week 1: `ApprovalRequest`/`ApprovalDecisionRequest` no longer accept a client-supplied `requestedByUserId`/`decidedByUserId` — the acting identity is always derived from the authenticated JWT now, closing an IDOR-style issue where any client could previously claim to be acting as any user.
- [x] Protections against common risk categories reviewed and documented: injection (parameterized queries throughout, nothing to inject into), broken access control (default-deny `anyRequest().authenticated()`), sensitive data exposure (no DTO ever carries a password hash or token; `AuditService` is the sole, reviewed write path to the audit log), insecure configuration (CORS is an explicit allowlist, not a wildcard with credentials; no committed secrets, consistent with Week 1).
- [x] Integration tests for authorization boundaries (`AuthorizationIntegrationTest`): drives real logins and real JWTs through anonymous access, role-gated actions, both ownership rules (project ownership, task assignment), and the self-approval rule, asserting both the forbidden case and the legitimate case for each.
- [x] Role and permission matrix (`docs/role-permission-matrix.md`) and a design-level security report (`docs/security-report.md`) covering mitigations and explicitly-deferred known limitations.

### Notes

- A related fix made during this pass: a `MANAGER` who creates a project is now automatically added as that project's `OWNER` member (`ProjectServiceImpl.create`). Without this, a freshly created project would have zero members, and the ownership-based authorization rule on membership management would make it impossible for its own creator to ever add anyone to it.
- Known, deliberately deferred security gaps (not missed, documented): no login rate limiting/account lockout yet, no admin-facing endpoint to query the audit log yet, activation/reset tokens are returned directly in API responses rather than emailed (this project has no mail server), and both the idempotency cache and audit log are single-instance/in-process for now. Full list in `docs/security-report.md`.
- Testing in this pass was scoped to the explicit Week 2 deliverable (authorization-boundary integration tests) rather than full unit/controller-layer coverage, which is Week 4 scope per the assignment.
