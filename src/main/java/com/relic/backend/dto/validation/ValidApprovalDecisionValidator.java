package com.relic.backend.dto.validation;

import com.relic.backend.dto.ApprovalDecisionRequest;
import com.relic.backend.entity.ApprovalStatus;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidApprovalDecisionValidator implements ConstraintValidator<ValidApprovalDecision, ApprovalDecisionRequest> {

    @Override
    public boolean isValid(ApprovalDecisionRequest request, ConstraintValidatorContext context) {
        if (request == null || request.status() == null) {
            return true;
        }
        if (request.status() == ApprovalStatus.REJECTED) {
            return request.comments() != null && !request.comments().isBlank();
        }
        return true;
    }
}
