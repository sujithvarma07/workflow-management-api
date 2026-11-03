package com.relic.backend.service;

import com.relic.backend.dto.ApprovalDecisionRequest;
import com.relic.backend.dto.ApprovalRequest;
import com.relic.backend.dto.ApprovalResponse;
import com.relic.backend.entity.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ApprovalService {

    ApprovalResponse requestApproval(Long taskId, ApprovalRequest request);

    ApprovalResponse decide(Long approvalId, ApprovalDecisionRequest request);

    Page<ApprovalResponse> getAll(Long taskId, ApprovalStatus status, Pageable pageable);

    ApprovalResponse getById(Long id);
}
