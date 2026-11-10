package com.relic.backend.dto;

// activationToken is returned directly in the response because this
// project has no email-sending infrastructure to deliver it through. In a
// real deployment this token would be emailed to the user and never
// returned by the API itself - this is a deliberate, documented
// simplification for a project with no mail server, not a real design.
public record RegisterResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String status,
        String activationToken
) {
}
