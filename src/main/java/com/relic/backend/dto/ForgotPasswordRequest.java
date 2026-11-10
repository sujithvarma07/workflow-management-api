package com.relic.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(

        @NotBlank(message = "username is required")
        String username
) {
}
