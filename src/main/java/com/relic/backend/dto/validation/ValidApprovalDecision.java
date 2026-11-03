package com.relic.backend.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Cross-field rule for an approval decision: rejecting an approval must be
 * accompanied by a non-blank comment explaining why, so the audit trail
 * never has a rejection with no reason attached.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidApprovalDecisionValidator.class)
@Documented
public @interface ValidApprovalDecision {
    String message() default "comments are required when rejecting an approval";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
