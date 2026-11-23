package com.relic.backend.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * The "safe replay procedure" for messages that ended up on the dead-letter
 * queue after exhausting their retries: drain the DLQ and republish each
 * message, unmodified, to the original exchange/routing key so it goes
 * through TaskCompletedMessageListener again from the top - including its
 * ProcessedMessage dedup check, which is what makes replaying safe even if
 * the original delivery partially succeeded before failing. Intended to be
 * run manually (via the admin endpoint below) after whatever caused the
 * failures - a bug, a downstream outage - has actually been fixed; replaying
 * blindly into a still-broken dependency just dead-letters the same messages
 * again.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeadLetterReplayService {

    private static final int MAX_MESSAGES_PER_CALL = 500;

    private final RabbitTemplate rabbitTemplate;

    public int replayTaskCompletedDeadLetters() {
        int replayed = 0;
        while (replayed < MAX_MESSAGES_PER_CALL) {
            Message message = rabbitTemplate.receive(RabbitMqConfig.TASK_COMPLETED_DLQ);
            if (message == null) {
                break;
            }
            rabbitTemplate.send(RabbitMqConfig.EVENTS_EXCHANGE, RabbitMqConfig.TASK_COMPLETED_ROUTING_KEY, message);
            replayed++;
        }
        log.info("Replayed {} message(s) from {}", replayed, RabbitMqConfig.TASK_COMPLETED_DLQ);
        return replayed;
    }
}
