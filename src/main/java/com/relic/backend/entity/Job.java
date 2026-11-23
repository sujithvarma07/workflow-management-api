package com.relic.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A background job record - what's being tracked here is the job itself
 * (a bulk import, a report run), not any individual item within it.
 * resultSummary / errorDetails are plain text (a small JSON blob for a
 * report's results, or a newline-joined list of per-item error messages for
 * an import) rather than a separate table, since a job's output is read as a
 * whole, not queried piece by piece.
 */
@Entity
@Table(name = "job")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private JobStatus status;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "total_items")
    private Integer totalItems;

    @Column(name = "success_count")
    private Integer successCount;

    @Column(name = "failure_count")
    private Integer failureCount;

    @Lob
    @Column(name = "result_summary")
    private String resultSummary;

    @Lob
    @Column(name = "error_details")
    private String errorDetails;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.status == null) {
            this.status = JobStatus.QUEUED;
        }
    }
}
