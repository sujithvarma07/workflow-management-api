package com.relic.backend.dto;

import com.relic.backend.entity.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record TaskStatusUpdateRequest(

        @NotNull(message = "status is required")
        TaskStatus status
) {
}
