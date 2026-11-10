package com.relic.backend.dto;

import com.relic.backend.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UserStatusUpdateRequest(

        @NotNull(message = "status is required")
        UserStatus status
) {
}
