# Entity Relationship Diagram

This covers the five entities built in Week 1: `User`, `Project`, `ProjectMember`, `Task`, `Assignment`, and `Approval`. `ProjectMember` and `Assignment` are join entities that carry their own attributes, which is why they're modeled as first-class entities rather than plain `@ManyToMany` collections.

```
+-------------+           +------------------+           +-------------+
|    User     |1---------*|  ProjectMember   |*---------1|   Project   |
+-------------+           +------------------+           +-------------+
| id (PK)     |           | id (PK)          |           | id (PK)     |
| username    |           | project_id (FK)  |           | name        |
| email       |           | user_id (FK)     |           | description |
| full_name   |           | role_in_project  |           | status      |
| role        |           | joined_at        |           | created_at  |
| created_at  |           +------------------+           | version     |
+-------------+                                           +-------------+
      |1                                                        |1
      |                                                         |
      *                                                         *
+------------------+                                      +-------------+
|   Assignment      |                                      |    Task     |
+------------------+                                      +-------------+
| id (PK)          |                                      | id (PK)     |
| task_id (FK)      *--------------------------------------| project_id (FK) |
| user_id (FK)      |                                      | title       |
| assigned_at       |                                      | description |
| unassigned_at     |                                      | status      |
| active            |                                      | priority    |
+------------------+                                      | due_date    |
                                                            | created_at  |
      +-----------------------------------------------------| version     |
      |1                                                     +-------------+
      *
+------------------+
|    Approval       |
+------------------+
| id (PK)           |
| task_id (FK)       |
| requested_by (FK -> User) |
| decided_by (FK -> User, nullable) |
| status             |
| comments            |
| requested_at        |
| decided_at          |
| version             |
+------------------+
```

## Relationships

- **User to ProjectMember**: one-to-many. A user can be a member of many projects.
- **Project to ProjectMember**: one-to-many. A project can have many members.
  - Together, `ProjectMember` implements a **many-to-many** relationship between `User` and `Project`, with `role_in_project` (OWNER / CONTRIBUTOR / VIEWER) as the attribute that attaches to the membership itself rather than to either side. A unique constraint on `(project_id, user_id)` prevents the same user being added to the same project twice.
- **Project to Task**: one-to-many. A task always belongs to exactly one project (`task.project_id` is `NOT NULL`).
- **Task to Assignment**: one-to-many, and **User to Assignment**: one-to-many — together these implement a **many-to-many** relationship between `Task` and `User` across time, since `Assignment` keeps a row per assignment event (`assigned_at`/`unassigned_at`/`active`) rather than overwriting a single "current assignee" column. That history is what lets `GET /api/v1/tasks/{taskId}/assignments` show who has ever worked on a task, not just who currently does.
- **Task to Approval**: one-to-many. A task can accumulate multiple approval requests over its lifetime (e.g. a rejected request followed by a second one after rework), but only one may be `PENDING` at a time (enforced in `ApprovalServiceImpl`, not at the database level, since "at most one row matching a condition" isn't expressible as a plain unique constraint).
- **User to Approval**: two separate many-to-one relationships from the same entity — `requested_by` and `decided_by` — since the user who asks for a review and the user who grants or denies it are different people playing different roles against the same approval record.

## Keys, constraints, and indexes

- Every table uses a surrogate `BIGINT` identity primary key (`id`), rather than a natural key, so none of the unique business identifiers (username, email, project name) need to double as the key other tables reference.
- Unique constraints: `app_user.username`, `app_user.email`, `project.name`, `project_member.(project_id, user_id)`.
- Foreign keys: `project_member.project_id -> project.id`, `project_member.user_id -> app_user.id`, `task.project_id -> project.id`, `assignment.task_id -> task.id`, `assignment.user_id -> app_user.id`, `approval.task_id -> task.id`, `approval.requested_by -> app_user.id`, `approval.decided_by -> app_user.id`.
- Foreign key columns are implicitly indexed by most relational databases (PostgreSQL does not index them automatically, so this is one of the first things called out in `docs/concurrency-design.md` and left as a deliberate, explicit indexing task rather than assumed) — `task.project_id`, `assignment.task_id`, `assignment.user_id`, and `approval.task_id` are the ones queried most often via `findByProjectId` / `findByTaskId`, and are the first candidates for explicit indexes once there's real data volume to validate against (the Week 4 scope for this project covers that profiling).
