package com.relic.backend.integration;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;

/**
 * Calls an external system (e.g. a customer-facing status page, a partner's
 * webhook) to report a completed task. This class does NOT swallow failures
 * or retry internally - it is only ever invoked from
 * TaskCompletedMessageListener, and that caller's own retry-then-dead-letter
 * handling (RabbitMqConfig's listener container retry advice) is the single
 * place that decides what happens after a failure. Swallowing the exception
 * here instead would make every failure look like a success to the message
 * listener, and a genuinely failing external call would never reach the
 * dead-letter queue for an operator to notice and replay.
 *
 * The one thing handled at this layer is the circuit breaker
 * ("externalNotification", configured in application.yml): once the
 * external service is failing consistently, it fails fast with
 * CallNotPermittedException during the open-circuit window instead of
 * attempting (and waiting out the timeout on) another doomed HTTP call -
 * that's a concern specific to this one HTTP dependency, not something the
 * generic message-retry layer above it should need to know about.
 */
@Component
@RequiredArgsConstructor
public class ExternalNotificationClient {

    private final WebClient externalApiWebClient;

    @CircuitBreaker(name = "externalNotification")
    public void notifyTaskCompleted(Long taskId, String taskTitle, Long projectId) {
        Map<String, Object> payload = Map.of(
                "event", "TASK_COMPLETED",
                "taskId", taskId,
                "taskTitle", taskTitle,
                "projectId", projectId,
                "occurredAt", Instant.now().toString());

        externalApiWebClient.post()
                .uri("/notifications")
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                // .block() is acceptable here: this method only ever runs on
                // a RabbitMQ listener container thread (see
                // TaskCompletedMessageListener), never on a request-handling
                // thread, so blocking it does not tie up request capacity.
                .block();
    }
}
