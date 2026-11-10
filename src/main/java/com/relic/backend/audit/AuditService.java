package com.relic.backend.audit;

import com.relic.backend.entity.AuditLog;
import com.relic.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place that writes to audit_log. Centralizing it here - rather
 * than letting every service call the repository directly - is what makes
 * "never log a password or token" an enforceable rule instead of a
 * convention every call site has to remember: this class's "details"
 * parameter is documented as short and non-sensitive, and there is nowhere
 * else a caller could slip a secret into the audit trail.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String username, String action, String resourceType, String resourceId, String outcome, String details) {
        // REQUIRES_NEW: an audit entry for a failed operation (e.g. a
        // rejected login) must survive even though the surrounding
        // transaction, if any, is about to roll back.
        AuditLog entry = AuditLog.builder()
                .username(username)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .outcome(outcome)
                .details(details)
                .build();
        auditLogRepository.save(entry);
    }

    public void success(String username, String action, String resourceType, String resourceId, String details) {
        record(username, action, resourceType, resourceId, "SUCCESS", details);
    }

    public void failure(String username, String action, String resourceType, String resourceId, String details) {
        record(username, action, resourceType, resourceId, "FAILURE", details);
    }
}
