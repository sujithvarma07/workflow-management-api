package com.relic.backend.security;

import org.springframework.security.core.AuthenticationException;

/**
 * Covers an invalid or expired activation token, password-reset token, or
 * refresh token - all three are "a secret the client presented doesn't
 * check out," which is an authentication failure (401), not a validation
 * error (400) or a missing resource (404): the request was well-formed, it
 * just doesn't prove what it claims to.
 */
public class InvalidTokenException extends AuthenticationException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
