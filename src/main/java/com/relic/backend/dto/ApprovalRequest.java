package com.relic.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApprovalRequest(

        @NotNull(message = "requestedByUserId is required")
        Long requestedByUserId,

        @Size(max = 2000, message = "comments must be at most 2000 characters")
        String comments
) {
}
