# Week 3 Reliability Report

This covers the design of everything added in Week 3: external API integration,
asynchronous bulk/report jobs, message-broker-based eventing with dead-letter
handling, caching, health/readiness probes, and request correlation.

## 1. External API integration

`ExternalNotificationClient` calls an external notification endpoint over
`WebClient` (`WebClientConfig`), configured with an explicit connect timeout
and an explicit response (read/write) timeout, both overridable via
`app.external-api.connect-timeout-ms` / `response-timeout-ms`. Two separate
timeouts are set deliberately: a network that silently drops packets never
trips a read timeout on its own (the connection attempt itself hangs), and a
server that accepts the connection but never responds never trips a connect
timeout.

The call is wrapped in a Resilience4j `@CircuitBreaker` (`externalNotification`
instance, configured in `application.yml`). There is intentionally no
`@Retry` and no fallback method at this layer. Retrying and dead-lettering
already happen one layer up, in the RabbitMQ listener that invokes this
client (see section 3) - a fallback here that swallowed the failure and
returned normally would make every failed external call look like a success
to that listener, which is exactly the bug the dead-letter queue exists to
catch. So the circuit breaker here does one job only: once the external
service is clearly unhealthy (per the sliding-window/failure-rate
configuration), stop sending it live traffic for a cooldown period and fail
fast with `CallNotPermittedException` instead, which propagates like any
other failure and is retried/dead-lettered the same way.

## 2. Asynchronous jobs (bulk task import, project reports)

Both job types follow the same shape: `JobController` accepts the request,
`JobServiceImpl` creates a `Job` row (status `QUEUED`) and hands off to a
`@Async("jobTaskExecutor")` processor, and returns `202 Accepted` with the
job id immediately. The caller polls `GET /api/v1/jobs/{id}` for status.

`jobTaskExecutor` (`AsyncConfig`) is a bounded `ThreadPoolTaskExecutor`
(core 2, max 4, queue 50, `CallerRunsPolicy`) rather than Spring's default
unbounded `SimpleAsyncTaskExecutor`, so a burst of job submissions can't
spawn unbounded threads.

Bulk task import processes items in fixed-size chunks (default 50,
`app.batch.chunk-size`), each chunk committed in its own
`REQUIRES_NEW` transaction (`BulkTaskImportChunkService`). This bounds how
much the Hibernate persistence context grows per transaction and means one
bad chunk failing outright does not roll back chunks that already committed
successfully. Within a chunk, each item is also individually try/caught, so
one bad row doesn't abort the rest of the chunk either - the job finishes
with a per-item success/failure tally and up to 200 recorded error messages,
rather than succeeding or failing as an all-or-nothing unit.

This is chunked, not streamed: the whole request body is still read into
memory as a single JSON array before processing starts, so it bounds memory
growth *during* processing, not the size of the request itself. A request
large enough to matter at that stage (hundreds of thousands of rows) would
need a different ingestion path - a streamed upload format - which is out of
scope for this week and is flagged here rather than silently assumed away.
`BulkTaskImportRequest` caps a single request at 2000 items as a concrete
guardrail in the meantime.

Both `BulkTaskImportProcessor` and `ProjectReportProcessor` are careful about
a specific Spring AOP pitfall: `@Transactional` and `@Async` only take effect
through Spring's generated proxy, so a method calling another
`@Transactional`/`@Async` method on `this` (same class) silently skips the
proxy and the annotation does nothing. Both processors are therefore thin
orchestrators that only ever call out to a *separate* bean
(`BulkTaskImportChunkService` / `ProjectReportJobService`) for every
transactional step, which is a genuine cross-bean call and does go through
the proxy.

## 3. Messaging: events, dead-lettering, idempotent consumption

Completing a task (via approval, see `ApprovalServiceImpl`) publishes an
in-process `TaskCompletedApplicationEvent`. `TaskCompletedEventPublisher`
listens for it with `@TransactionalEventListener(phase = AFTER_COMMIT)` and
only then publishes an actual message to RabbitMQ. This two-step indirection
exists so that a transaction which updates the task but then rolls back for
some other reason never results in a message going out for a change that
didn't actually happen.

Topology (`RabbitMqConfig`): a topic exchange (`workflow.events`), a queue
bound to `task.completed` (`workflow.events.task-completed`), and a
corresponding dead-letter exchange/queue
(`workflow.events.dlx` / `workflow.events.task-completed.dlq`). The listener
container factory is wired with a stateless `RetryOperationsInterceptor`
(max 3 attempts, exponential backoff) and a `RepublishMessageRecoverer` that
routes a message to the dead-letter queue once retries are exhausted, rather
than requeueing it forever or silently dropping it.

`TaskCompletedMessageListener` checks `ProcessedMessageRepository` for the
incoming message's id *inside the same transaction* as the work the message
triggers (calling `ExternalNotificationClient`, marking the message
processed). RabbitMQ only guarantees at-least-once delivery, so a message
can legitimately be redelivered after a consumer crash between doing the
work and acknowledging it; this dedup check is what makes redelivery safe to
just skip rather than reprocess.

`DeadLetterReplayService` drains the dead-letter queue (up to 500 messages
per call) and republishes each one to the original exchange/routing key,
exposed to admins via `POST /api/v1/admin/events/replay-dead-letters`. Replay
is safe against double-processing for the same reason consumption is: the
dedup table is checked again on the replayed delivery.

## 4. Caching

`CacheConfig` registers three named caches - `projects`, `tasks`, `users` -
each keyed by entity id, backed by `ConcurrentMapCacheManager` in dev/test
and a real `RedisCacheManager` (10 minute TTL) in the `postgres` profile.
`getById` on `ProjectServiceImpl`/`TaskServiceImpl`/`UserServiceImpl` is
`@Cacheable`; their own `update`/`delete` (and `updateStatus` for tasks and
users) carry a matching `@CacheEvict`.

One path bypasses that symmetry on purpose and is handled explicitly:
`ApprovalServiceImpl` moves a task to `DONE` by saving it directly via
`TaskRepository`, not through `TaskServiceImpl`, so `TaskServiceImpl`'s
`@CacheEvict` annotations never fire for that change. `ApprovalServiceImpl`
evicts the `tasks` cache entry for that task id itself, via an injected
`CacheManager`, immediately after the save. The 10 minute Redis TTL is kept
as a backstop in case a similar bypass is ever added without the matching
manual eviction, but it is not meant to be the primary invalidation
mechanism - a stale read for up to 10 minutes would be the actual
consequence of ever missing one of these.

## 5. Health and readiness

`management.endpoint.health.probes.enabled: true` plus
`management.health.livenessstate`/`readinessstate.enabled: true` expose
Kubernetes-style `/actuator/health/liveness` and `/actuator/health/readiness`
groups. `management.health.rabbit.enabled` and `.redis.enabled` are turned on
so the overall health status (and the readiness group specifically) reflects
real connectivity to both - in an environment where either is actually down,
readiness will correctly report `DOWN` rather than paper over it.

## 6. Correlation IDs / structured logging

`CorrelationIdFilter` (highest filter precedence, so it runs before Spring
Security's own filters) reads an inbound `X-Correlation-Id` header or
generates a UUID, puts it in the SLF4J MDC for the request's lifetime, and
echoes it back on the response. The console logging pattern
(`logging.pattern.console`) includes it on every line, so every log emitted
while handling a given request - security filter rejections included - can
be grepped out by that one id, and a caller that sends its own
`X-Correlation-Id` can trace a request across this service and whatever
called it.

## 7. What has not been, and cannot be, verified live from this environment

No Maven/JDK, no RabbitMQ broker, no Redis instance, and no real external
HTTP endpoint are reachable from the environment this code was written in.
Concretely, that means the following have been written to each
technology's documented contract and reviewed by hand, but have not been
exercised against a live instance:

- RabbitMQ: exchange/queue/DLX/DLQ declaration, the retry-then-dead-letter
  interceptor, `@TransactionalEventListener(AFTER_COMMIT)` actually firing
  a publish, dead-letter replay.
- Redis: the `RedisCacheManager` configuration in the `postgres` profile
  (TTL, null-value handling, key serialization).
- The external notification endpoint: real request/response behavior,
  and the circuit breaker's actual state transitions under real failures.
- `GET /actuator/health/readiness` genuinely reporting `DOWN` when RabbitMQ
  or Redis is unreachable.

`AuthorizationIntegrationTest` (from Week 2) continues to run against the
in-memory H2 database under the `test` profile; it does not exercise any of
the Week 3 messaging/caching/external-call paths. To keep that test's
`@SpringBootTest` context able to start without a broker, the `test` profile
sets `spring.rabbitmq.listener.simple.auto-startup: false` (see
`application.yml`) - without it, the `@RabbitListener` container would
attempt to connect at context startup and log continuous connection-refused
retries in the background for the whole test run.

Everything above is written out as an explicit limitation rather than
presented as "tested," consistent with the project's existing policy of not
reporting results that were never produced by actually running the code.
