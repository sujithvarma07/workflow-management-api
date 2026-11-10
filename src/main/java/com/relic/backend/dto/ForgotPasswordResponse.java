package com.relic.backend.dto;

// Same documented simplification as RegisterResponse.activationToken: this
// would be emailed in a real deployment, not returned by the API.
public record ForgotPasswordResponse(
        String resetToken
) {
}
