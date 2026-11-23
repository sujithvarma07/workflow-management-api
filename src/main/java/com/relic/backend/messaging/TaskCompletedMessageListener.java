package com.relic.backend.messaging;

import com.relic.backend.entity.ProcessedMessage;
import com.relic.backend.integration.ExternalNotificationClient;
import com.relic.backend.repository.ProcessedMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskCompletedMessageListener {

    private final ProcessedMessageRepository processedMessageRepository;
    private final ExternalNotificationClient externalNotificationClient;

    @RabbitListener(queues = RabbitMqConfig.TASK_COMPLETED_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    @Transactional
    public void handle(TaskCompletedMessage message) {
        // Idempotency check and the "mark as processed" write happen in the
        // same transaction as whatever processing follows - if RabbitMQ
        // redelivers this exact message (its delivery guarantee is at-least-
        // once, never exactly-once), the second delivery finds its id
        // already recorded and is skipped, rather than sending a duplicate
        // external notification.
        if (processedMessageRepository.existsById(message.messageId())) {
            log.info("Skipping already-processed message {}", message.messageId());
            return;
        }

        externalNotificationClient.notifyTaskCompleted(message.taskId(), message.taskTitle(), message.projectId());

        processedMessageRepository.save(ProcessedMessage.builder()
                .messageId(message.messageId())
                .messageType("TASK_COMPLETED")
                .build());
    }
}
