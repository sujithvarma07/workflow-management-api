package com.relic.backend.dto;

import com.relic.backend.dto.validation.ValidApprovalDecision;
import com.relic.backend.entity.ApprovalStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@ValidApprovalDecision
public record ApprovalDecisionRequest(

        @NotNull(message = "decidedByUserId is required")
        Long decidedByUserId,

        @NotNull(message = "status is required")
        ApprovalStatus status,

        @Size(max = 2000, message = "comments must be at most 2000 characters")
        String comments
) {
}
