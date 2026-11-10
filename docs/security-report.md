# Security Testing Report

This is a design-level security review of Week 2's authentication/authorization work - threats considered, how each is actually mitigated in this codebase (with the specific class responsible), and what's explicitly deferred rather than quietly skipped. It is not a penetration test log or a tool-generated scan, since there is no running, network-reachable deployment of this application to test against from this environment; everything below is verifiable by reading the referenced source.

## Authentication

| Risk | Mitigation |
|---|---|
| Passwords stored recoverably | Only a BCrypt hash is ever persisted (`User.passwordHash`); `PasswordEncoderConfig` configures `BCryptPasswordEncoder`, which is adaptive (tunable work factor) and self-salting, unlike a plain fast hash. |
| Credential stuffing / brute force | **Not implemented yet.** There is no account lockout or rate limiting on `/api/v1/auth/login` in this codebase. This is a real, acknowledged gap - see Known Limitations below. |
| Session fixation / token replay after logout | Sessions are stateless (JWT, no server-side session); `RefreshTokenService.revoke`/`revokeAllForUser` let a logout or password reset actually invalidate a refresh token rather than relying on it expiring naturally. |
| Forged or tampered access token | JWTs are signed with HMAC-SHA256 using `JWT_SECRET` (`JwtService`), a required environment variable with no default and a minimum-length check enforced at startup (`Keys.hmacShaKeyFor` throws if the key is too short for HS256). |
| Leaked refresh token used indefinitely | Refresh tokens are opaque, rotated on every use (`RefreshTokenService.rotate`), and only a SHA-256 hash is stored - a stolen token can be used exactly once before rotation invalidates it, and a stolen database backup doesn't yield usable tokens directly. |
| Privilege escalation via self-registration | `POST /api/v1/auth/register` hardcodes the new account's role to `DEVELOPER` in `AuthServiceImpl.register` - the role is never read from the request body, so there is no `role` field a client could set to request `ADMIN` or `MANAGER` for themselves. |
| Account enumeration via login error messages | `AuthServiceImpl.login` returns the same generic "Invalid username or password" message whether the username doesn't exist, the password is wrong, or the account isn't active yet. |

## Authorization

| Risk | Mitigation |
|---|---|
| Broken access control / missing authorization checks | `SecurityConfig` defaults to `anyRequest().authenticated()` - a new endpoint is private by default and has to be explicitly added to `PUBLIC_ENDPOINTS` to be reachable without a token, rather than the reverse. |
| Horizontal privilege escalation (acting as another user) | Fixed directly in this pass: `ApprovalRequest`/`ApprovalDecisionRequest` no longer accept a client-supplied `requestedByUserId`/`decidedByUserId` (Week 1 left this as an IDOR risk - see `docs/progress.md`'s Week 2 notes). The acting user is now always derived from the authenticated JWT (`Authentication.getName()` in the controller), never from the request body. |
| Vertical privilege escalation (role-gated actions) | `@PreAuthorize` on every state-changing controller method, backed by `@EnableMethodSecurity` - e.g. only `ADMIN`/`MANAGER` can create a task, only `ADMIN` can suspend a user. |
| Record-level (ownership) bypass | `AuthorizationService` (`authz` bean) backs the two ownership rules that a plain role check can't express: project ownership and task-assignment ownership. See `docs/role-permission-matrix.md`. |
| Conflict of interest in the approval workflow | `ApprovalServiceImpl.decide` explicitly rejects a decision where `decidedBy` equals the approval's `requestedBy`, regardless of role - self-approval is blocked as a business rule, not left to the honor system. |

## Common risk categories (OWASP-style)

- **Injection** — every query in this codebase goes through Spring Data JPA/Hibernate with parameter binding (derived query methods, no string-concatenated JPQL or native SQL anywhere in `repository/`). There is no raw SQL assembled from request input to inject into.
- **Broken access control** — covered above.
- **Sensitive data exposure** — no DTO (`UserResponse` included) ever contains `passwordHash`, a refresh token, or a JWT; `AuditLog.details` is documented and reviewed call-by-call to never carry a password or token value (see `AuditService`'s class comment). `application.yml` and `docker-compose.yml`/`.env.example` (once Week 4 adds them) contain no real credential - everything is a required, no-default environment variable, exactly as Week 1 already established for the database connection.
- **Insecure configuration** — CORS is an explicit, env-configured origin allowlist (`CORS_ALLOWED_ORIGINS`), not `*` with credentials allowed (see the comment in `SecurityConfig.corsConfigurationSource`); CSRF is disabled deliberately (stateless, token-authenticated API, not a cookie/session-based one - CSRF protects the latter, not the former); the H2 console is reachable only under the `dev` profile and only same-origin (`frameOptions.sameOrigin`).
- **Insecure deserialization** — not applicable; all request bodies are deserialized into fixed Java record DTOs by Jackson via Spring's standard `@RequestBody` handling, not generic/polymorphic deserialization.

## Known limitations (deferred, not missed)

- No login rate limiting / account lockout yet. A fixed-window, per-IP limiter (the same style as `IdempotencyFilter`'s in-memory approach) is the natural fit for `/api/v1/auth/login` specifically, and is left for a later pass rather than built speculatively now.
- No structured audit-log review tooling (an admin-facing endpoint to query `audit_log`) - entries are written and queryable via the repository/database directly, but there's no `GET /api/v1/audit-logs` endpoint yet.
- Password reset and account activation tokens are returned directly in the API response rather than emailed, because this project has no email-sending infrastructure. This is a deliberate, documented simplification (see `RegisterResponse` and `ForgotPasswordResponse`), not a production pattern - the comment on both DTOs says so explicitly, and the fix in a real deployment is to send the token to the verified email address instead of returning it in the response body at all.
- No distributed/shared idempotency store yet - `IdempotencyFilter`'s cache is in-process (correct for a single instance, same trade-off as a typical in-memory rate limiter), and a multi-instance deployment would move it to a shared store (e.g. Redis) behind the same filter without changing how callers use the `Idempotency-Key` header.
