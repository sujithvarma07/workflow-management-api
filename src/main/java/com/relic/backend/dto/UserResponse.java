package com.relic.backend.dto;

import com.relic.backend.entity.UserRole;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        UserRole role,
        Instant createdAt
) {
}
