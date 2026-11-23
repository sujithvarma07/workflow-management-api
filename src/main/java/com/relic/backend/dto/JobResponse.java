package com.relic.backend.dto;

import com.relic.backend.entity.JobStatus;
import com.relic.backend.entity.JobType;

import java.time.Instant;

public record JobResponse(
        Long id,
        JobType type,
        JobStatus status,
        String createdBy,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Integer totalItems,
        Integer successCount,
        Integer failureCount,
        String resultSummary,
        String errorDetails
) {
}
