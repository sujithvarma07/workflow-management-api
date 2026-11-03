package com.relic.backend.dto;

import com.relic.backend.entity.ApprovalStatus;

import java.time.Instant;

public record ApprovalResponse(
        Long id,
        Long taskId,
        String taskTitle,
        Long requestedByUserId,
        String requestedByUsername,
        Long decidedByUserId,
        String decidedByUsername,
        ApprovalStatus status,
        String comments,
        Instant requestedAt,
        Instant decidedAt,
        Long version
) {
}
