package com.relic.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One row per message id this application has successfully processed.
 * RabbitMQ (like virtually every broker) guarantees at-least-once delivery,
 * not exactly-once - a consumer can see the same message twice (a redelivery
 * after a connection drop before the ack was recorded, for example). Checking
 * this table before acting on a message, inside the same transaction as the
 * action itself, is what turns "at least once delivered" into "effectively
 * exactly once processed."
 */
@Entity
@Table(name = "processed_message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedMessage {

    @Id
    @Column(name = "message_id", length = 64)
    private String messageId;

    @Column(nullable = false, length = 50)
    private String messageType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @PrePersist
    protected void onCreate() {
        this.processedAt = Instant.now();
    }
}
