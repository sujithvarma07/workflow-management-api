package com.relic.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ActivateAccountRequest(

        @NotBlank(message = "token is required")
        String token
) {
}
