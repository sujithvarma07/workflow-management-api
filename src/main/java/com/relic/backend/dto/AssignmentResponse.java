package com.relic.backend.dto;

import java.time.Instant;

public record AssignmentResponse(
        Long id,
        Long taskId,
        String taskTitle,
        Long userId,
        String username,
        Instant assignedAt,
        Instant unassignedAt,
        boolean active
) {
}
