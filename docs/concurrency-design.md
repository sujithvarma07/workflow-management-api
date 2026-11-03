# Database Transaction and Concurrency Design Report

This covers how transactions are scoped in this application, what propagation and rollback behavior that implies, and the specific concurrency problems that show up in a workflow with shared mutable state (project membership, task status, approval decisions) and how each is actually handled.

## Transaction boundaries

Every write-path method on a service implementation is annotated `@Transactional`; every read-path method is `@Transactional(readOnly = true)`. The boundary is drawn at the service layer, not the controller or repository layer, for two reasons:

- A controller method should not need to know anything about transactions — it calls one service method and the whole operation either committed or didn't by the time it returns.
- A repository method is too fine-grained a unit: several of the operations here (for example, deciding an approval, which both updates the `Approval` row and conditionally updates the `Task` row it belongs to) have to succeed or fail together. If each repository call opened its own transaction, a failure partway through could leave the approval marked `APPROVED` while the task never actually moved to `DONE`.

### Propagation

Every `@Transactional` method in this codebase uses Spring's default propagation, `REQUIRED`: if a transaction is already active when the method is entered, it joins that transaction; if not, a new one is started. None of the services call each other directly across layers in a way that would need a different propagation level (`REQUIRES_NEW`, `NESTED`, etc.) yet — the one case worth calling out is `ApprovalServiceImpl.decide(...)`, which both saves the `Approval` and, when the decision is `APPROVED`, saves the `Task` it belongs to, inside the same method and therefore the same transaction. That's deliberate: there's no scenario where the approval decision should be recorded but the resulting task-status change should not be (or vice versa), so they share one atomic unit rather than being split into two calls that could partially succeed.

### Rollback behavior

By default, a Spring-managed transaction rolls back on any unchecked exception and commits otherwise. All of the custom exceptions in this codebase (`ResourceNotFoundException`, `DuplicateResourceException`, `InvalidStateTransitionException`) extend `RuntimeException`, so throwing any of them from inside a `@Transactional` method rolls back everything that method had done so far. Concretely: if `ApprovalServiceImpl.decide(...)` found the approval, validated it, and then failed to find the deciding user, nothing is partially committed — the approval is not marked decided, because the exception is thrown before `approvalRepository.save(...)` is ever called, and even if it had already been called, the surrounding transaction would roll that write back too.

No method in this codebase currently needs `@Transactional(rollbackFor = ...)` or `noRollbackFor` overrides, since there are no checked exceptions in play and no case where a thrown exception should still result in a commit.

## Concurrency problems and how they're handled

### Lost updates on shared rows

`Project` and `Task` both carry a `@Version` column. Hibernate includes the version in the `WHERE` clause of every `UPDATE` it issues for that entity, and increments it on success; if two requests read the same row, then both try to write back their own update, the first write succeeds and bumps the version, and the second write's `WHERE version = <stale value>` matches zero rows, which Hibernate reports as `ObjectOptimisticLockingFailureException`. `GlobalExceptionHandler` converts that into a `409 Conflict` with a message telling the client to retry against the current version, rather than letting either the project or task update silently clobber the other's change — which is exactly the scenario two managers editing the same project at once, or two automated processes updating the same task, would otherwise hit.

`Approval` also carries `@Version` for the same reason: two decision requests racing on the same approval would otherwise risk one being silently overwritten; now the second one fails with a 409 instead.

Optimistic locking rather than a pessimistic row lock (`SELECT ... FOR UPDATE`) is the right default here because conflicting writes to the same project or task are expected to be rare relative to how often that row is simply read — paying the cost of holding a database lock across every write, on the chance that a second writer shows up at the same instant, would be the wrong trade for this workload. If a future endpoint turns out to need stronger guarantees (for example, a bulk-assignment operation that must never partially apply), that would be the point to introduce pessimistic locking for that specific path rather than globally.

### Duplicate requests

A few business rules exist specifically to make retried or duplicate client requests fail loudly instead of creating duplicate state:

- Creating a user or project with a name/username/email that already exists throws `DuplicateResourceException` (409) rather than creating a second row.
- Adding the same user to the same project twice is rejected the same way, backed by the `(project_id, user_id)` unique constraint on `project_member` as a second line of defense if two requests somehow both pass the application-level check at nearly the same time.
- Assigning the same user to the same task while they already have an active assignment is rejected, so retrying a slow "assign" request doesn't produce two active assignment rows for the same person on the same task.
- Requesting approval for a task that already has a `PENDING` approval is rejected, so a client that retries a request-approval call after a timeout doesn't end up with two open approval requests competing to be decided.

None of these duplicate-prevention checks are, by themselves, airtight against a true race (two requests passing the existence check at the same instant, before either has written its row) without a corresponding database-level unique constraint — which is why `project_member` has one. `app_user.username`/`email` and `project.name` are also enforced as database-level unique constraints for the same reason: the application-level `existsBy...` check is for returning a clean 409 with a useful message, not the actual guarantee against duplicates, which the database's unique index provides even if two requests land at the exact same moment.

### Invalid state transitions under concurrent requests

Task status changes and approval decisions both validate the *current* state before allowing a transition (`TaskServiceImpl.updateStatus`, `ApprovalServiceImpl.decide`). Combined with `@Version` on both entities, this means: if two requests both try to act on a task or approval that's in a state where only one of them is a valid transition, the one that commits first wins, and the second either fails the state-transition check (if it read the stale state before the first one committed) or fails the optimistic-lock check (if it read the correct state but the row changed underneath it by the time it tried to write). Either way, the failure is a clear 409 rather than two conflicting state changes both silently taking effect.
