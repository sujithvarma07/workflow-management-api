package com.relic.backend.dto;

import com.relic.backend.entity.UserRole;
import com.relic.backend.entity.UserStatus;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        UserRole role,
        UserStatus status,
        Instant createdAt,
        Instant lastLoginAt
) {
}
