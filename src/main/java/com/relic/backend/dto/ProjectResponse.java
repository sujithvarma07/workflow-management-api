package com.relic.backend.dto;

import com.relic.backend.entity.ProjectStatus;

import java.time.Instant;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        ProjectStatus status,
        Instant createdAt,
        Long version
) {
}
