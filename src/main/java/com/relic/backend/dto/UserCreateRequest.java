package com.relic.backend.dto;

import com.relic.backend.dto.validation.UniqueUsername;
import com.relic.backend.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// This is the ADMIN-only path for creating an account directly (already
// ACTIVE, no activation email/token round trip) - distinct from
// POST /api/v1/auth/register, which is how someone creates their own
// DEVELOPER account and must activate it before logging in.
public record UserCreateRequest(

        @NotBlank(message = "username is required")
        @Size(min = 3, max = 50, message = "username must be between 3 and 50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_.]+$", message = "username may only contain letters, numbers, dots, and underscores")
        @UniqueUsername
        String username,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email,

        @NotBlank(message = "fullName is required")
        @Size(max = 150, message = "fullName must be at most 150 characters")
        String fullName,

        @NotNull(message = "role is required")
        UserRole role,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 100, message = "password must be at least 8 characters")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "password must contain at least one letter and one digit")
        String password
) {
}
