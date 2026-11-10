# Role and Permission Matrix

Three roles: `ADMIN`, `MANAGER`, `DEVELOPER`. A new account always starts as `DEVELOPER` (self-registration cannot request a higher role - see `docs/security-report.md`); only an existing `ADMIN` can promote or demote a user afterward.

| Action | ADMIN | MANAGER | DEVELOPER |
|---|---|---|---|
| Register / activate own account | n/a (bootstrap only) | yes | yes |
| View own profile (`GET /users/me`) | yes | yes | yes |
| Create / list / update / delete any user | yes | no | no |
| Suspend / reactivate a user | yes | no | no |
| Create / update / delete a project | yes | only if OWNER of that project (update/delete) | no |
| Add / remove a project member | yes | only if OWNER of that project | no |
| View projects, tasks, approvals (read) | yes | yes | yes |
| Create / update / delete a task | yes | yes | no |
| Change a task's status | yes | yes | only if actively assigned to that task |
| Assign / unassign a user to/from a task | yes | yes | no |
| Request approval on a task | yes | yes | only if actively assigned to that task |
| Approve / reject / cancel an approval | yes | yes, but never their own request | no |

## Notes on the ownership-based rows

Two rows above depend on the specific record, not just the role, which is why they're enforced with a database lookup (`AuthorizationService`, the `authz` bean referenced from `@PreAuthorize`) rather than a plain role check:

- **Project update/delete/membership** — a `MANAGER` can only act on a project where their `ProjectMember` row has `roleInProject = OWNER`. Being a `MANAGER` elsewhere in the system grants nothing here; a `MANAGER` who isn't that project's owner is treated the same as a `DEVELOPER` for these actions (denied).
- **Task status / approval request** — a `DEVELOPER` can only act on a task they are currently (`active = true`) assigned to via `Assignment`. This is what lets the system tell "my own work" apart from "anyone's work" for a role that otherwise has read-only access to everything.

Self-approval is a related but separate rule, checked in `ApprovalServiceImpl` rather than expressed in this table as a role/record combination: whoever decides an approval (`ADMIN` or `MANAGER`) can never be the same person who requested it, regardless of role.
