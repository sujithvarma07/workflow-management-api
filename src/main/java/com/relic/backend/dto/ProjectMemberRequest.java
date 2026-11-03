package com.relic.backend.dto;

import com.relic.backend.entity.ProjectMemberRole;
import jakarta.validation.constraints.NotNull;

public record ProjectMemberRequest(

        @NotNull(message = "userId is required")
        Long userId,

        @NotNull(message = "roleInProject is required")
        ProjectMemberRole roleInProject
) {
}
