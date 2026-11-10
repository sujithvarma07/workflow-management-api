package com.relic.backend.dto;

import jakarta.validation.constraints.Size;

// requestedByUserId is intentionally absent - before authentication existed
// (Week 1), the caller supplied it directly, which meant any client could
// claim to be requesting approval as any user simply by passing a
// different id (a classic broken-access-control / IDOR issue). Now that
// every request carries an authenticated identity, the requester is always
// derived from the JWT in ApprovalController, never taken from the body.
public record ApprovalRequest(

        @Size(max = 2000, message = "comments must be at most 2000 characters")
        String comments
) {
}
