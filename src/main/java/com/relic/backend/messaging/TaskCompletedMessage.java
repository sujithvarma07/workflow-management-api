package com.relic.backend.messaging;

import java.io.Serializable;
import java.time.Instant;

/** The wire format published to RabbitMQ and consumed back. messageId is
 * what ProcessedMessage dedups on - generated once, at publish time, so a
 * redelivery of the same broker message carries the same id. */
public record TaskCompletedMessage(
        String messageId,
        Long taskId,
        String taskTitle,
        Long projectId,
        Instant occurredAt
) implements Serializable {
}
