package com.relic.backend.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Bridges the in-process TaskCompletedApplicationEvent to an actual RabbitMQ
 * message, but only AFTER the transaction that raised it has committed - a
 * message published for an approval that then rolled back would notify an
 * external system about something that, as far as this application's own
 * data is concerned, never happened.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskCompletedEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCompleted(TaskCompletedApplicationEvent event) {
        TaskCompletedMessage message = new TaskCompletedMessage(
                UUID.randomUUID().toString(),
                event.getTaskId(),
                event.getTaskTitle(),
                event.getProjectId(),
                Instant.now());

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.EVENTS_EXCHANGE, RabbitMqConfig.TASK_COMPLETED_ROUTING_KEY, message);
        } catch (Exception e) {
            // Publishing itself failing (e.g. the broker is unreachable) is
            // logged, not thrown - the approval has already committed
            // successfully from the caller's point of view by the time this
            // listener runs, so there is nothing left to roll back, and an
            // external notification is explicitly a best-effort side effect
            // of that committed change, not a requirement for it.
            log.warn("Failed to publish TaskCompletedMessage for task {}: {}", event.getTaskId(), e.toString());
        }
    }
}
