package com.relic.backend.dto;

import jakarta.validation.constraints.NotNull;

public record AssignmentRequest(

        @NotNull(message = "userId is required")
        Long userId
) {
}
