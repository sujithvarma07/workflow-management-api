package com.relic.backend.controller;

import com.relic.backend.dto.ApprovalDecisionRequest;
import com.relic.backend.dto.ApprovalRequest;
import com.relic.backend.dto.ApprovalResponse;
import com.relic.backend.entity.ApprovalStatus;
import com.relic.backend.service.ApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping("/api/v1/tasks/{taskId}/approvals")
    public ResponseEntity<ApprovalResponse> requestApproval(
            @PathVariable Long taskId, @Valid @RequestBody ApprovalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(approvalService.requestApproval(taskId, request));
    }

    @PatchMapping("/api/v1/approvals/{id}/decision")
    public ApprovalResponse decide(@PathVariable Long id, @Valid @RequestBody ApprovalDecisionRequest request) {
        return approvalService.decide(id, request);
    }

    @GetMapping("/api/v1/approvals")
    public Page<ApprovalResponse> getAll(
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) ApprovalStatus status,
            @PageableDefault(size = 20, sort = "requestedAt,desc") Pageable pageable) {
        return approvalService.getAll(taskId, status, pageable);
    }

    @GetMapping("/api/v1/approvals/{id}")
    public ApprovalResponse getById(@PathVariable Long id) {
        return approvalService.getById(id);
    }
}
