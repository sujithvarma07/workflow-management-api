package com.relic.backend.dto;

import com.relic.backend.entity.ProjectMemberRole;

import java.time.Instant;

public record ProjectMemberResponse(
        Long id,
        Long projectId,
        Long userId,
        String username,
        ProjectMemberRole roleInProject,
        Instant joinedAt
) {
}
