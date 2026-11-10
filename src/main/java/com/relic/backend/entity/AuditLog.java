package com.relic.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One row per auditable action. "details" is a short, human-readable note -
 * it must never contain a password, token, or other secret; AuditService is
 * the only place that writes to this table, specifically so that rule has
 * one place to enforce rather than being trusted at every call site.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(length = 50)
    private String username;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "resource_id", length = 50)
    private String resourceId;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(length = 500)
    private String details;

    @PrePersist
    protected void onCreate() {
        this.timestamp = Instant.now();
    }
}
