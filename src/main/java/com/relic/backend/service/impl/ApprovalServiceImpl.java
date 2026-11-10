package com.relic.backend.service.impl;

import com.relic.backend.audit.AuditService;
import com.relic.backend.dto.ApprovalDecisionRequest;
import com.relic.backend.dto.ApprovalRequest;
import com.relic.backend.dto.ApprovalResponse;
import com.relic.backend.entity.Approval;
import com.relic.backend.entity.ApprovalStatus;
import com.relic.backend.entity.Task;
import com.relic.backend.entity.TaskStatus;
import com.relic.backend.entity.User;
import com.relic.backend.entity.UserRole;
import com.relic.backend.exception.DuplicateResourceException;
import com.relic.backend.exception.InvalidStateTransitionException;
import com.relic.backend.exception.ResourceNotFoundException;
import com.relic.backend.repository.ApprovalRepository;
import com.relic.backend.repository.AssignmentRepository;
import com.relic.backend.repository.TaskRepository;
import com.relic.backend.repository.UserRepository;
import com.relic.backend.service.ApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ApprovalServiceImpl implements ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final AssignmentRepository assignmentRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public ApprovalResponse requestApproval(Long taskId, ApprovalRequest request, String requestingUsername) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id " + taskId));
        User requestedBy = userRepository.findByUsernameIgnoreCase(requestingUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + requestingUsername));

        // Ownership-based rule: a DEVELOPER may only request approval on a
        // task they are actually, currently assigned to - they can't ask
        // for someone else's work to be reviewed. ADMIN and MANAGER are
        // exempt since requesting approval on a team member's behalf is a
        // legitimate part of their role.
        if (requestedBy.getRole() == UserRole.DEVELOPER
                && !assignmentRepository.existsByTaskIdAndUserIdAndActiveTrue(taskId, requestedBy.getId())) {
            throw new AccessDeniedException(
                    "Only a user actively assigned to this task may request its approval");
        }

        if (approvalRepository.existsByTaskIdAndStatus(taskId, ApprovalStatus.PENDING)) {
            throw new DuplicateResourceException(
                    "Task " + taskId + " already has a pending approval request");
        }

        Approval approval = Approval.builder()
                .task(task)
                .requestedBy(requestedBy)
                .comments(request.comments())
                .build();

        Approval saved = approvalRepository.save(approval);
        auditService.success(requestingUsername, "APPROVAL_REQUESTED", "Approval", String.valueOf(saved.getId()),
                "taskId=" + taskId);

        return toResponse(saved);
    }

    @Override
    @Transactional
    public ApprovalResponse decide(Long approvalId, ApprovalDecisionRequest request, String decidingUsername) {
        Approval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval not found with id " + approvalId));

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new InvalidStateTransitionException(
                    "Approval " + approvalId + " has already been decided (" + approval.getStatus() + ")");
        }
        if (request.status() == ApprovalStatus.PENDING) {
            throw new InvalidStateTransitionException("A decision must be APPROVED, REJECTED, or CANCELLED");
        }

        User decidedBy = userRepository.findByUsernameIgnoreCase(decidingUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + decidingUsername));

        // Self-approval is a business rule, not just an access-control
        // check - an ADMIN or MANAGER requesting approval on their own work
        // is allowed (see requestApproval), but deciding that same request
        // themselves defeats the point of a review. This is checked here,
        // against the actual requester on record, rather than relying on
        // the client to not do it.
        if (decidedBy.getId().equals(approval.getRequestedBy().getId())) {
            auditService.failure(decidingUsername, "APPROVAL_DECIDED", "Approval", String.valueOf(approvalId),
                    "self-approval blocked");
            throw new InvalidStateTransitionException("You cannot approve or reject your own request");
        }

        approval.setStatus(request.status());
        approval.setDecidedBy(decidedBy);
        approval.setDecidedAt(Instant.now());
        if (request.comments() != null && !request.comments().isBlank()) {
            approval.setComments(request.comments());
        }

        // Approving the request is what actually moves the underlying task
        // to DONE - the approval workflow is the gate on task completion,
        // not just a record kept alongside it. Rejecting leaves the task
        // exactly where it was so the assignee can keep working on it.
        if (request.status() == ApprovalStatus.APPROVED) {
            Task task = approval.getTask();
            if (task.getStatus() != TaskStatus.DONE) {
                task.setStatus(TaskStatus.DONE);
                taskRepository.save(task);
            }
        }

        Approval saved = approvalRepository.save(approval);
        auditService.success(decidingUsername, "APPROVAL_DECIDED", "Approval", String.valueOf(approvalId),
                "status=" + request.status());

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ApprovalResponse> getAll(Long taskId, ApprovalStatus status, Pageable pageable) {
        Page<Approval> page;
        if (taskId != null && status != null) {
            page = approvalRepository.findByTaskIdAndStatus(taskId, status, pageable);
        } else if (taskId != null) {
            page = approvalRepository.findByTaskId(taskId, pageable);
        } else if (status != null) {
            page = approvalRepository.findByStatus(status, pageable);
        } else {
            page = approvalRepository.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalResponse getById(Long id) {
        Approval approval = approvalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Approval not found with id " + id));
        return toResponse(approval);
    }

    private ApprovalResponse toResponse(Approval approval) {
        return new ApprovalResponse(
                approval.getId(),
                approval.getTask().getId(),
                approval.getTask().getTitle(),
                approval.getRequestedBy().getId(),
                approval.getRequestedBy().getUsername(),
                approval.getDecidedBy() != null ? approval.getDecidedBy().getId() : null,
                approval.getDecidedBy() != null ? approval.getDecidedBy().getUsername() : null,
                approval.getStatus(),
                approval.getComments(),
                approval.getRequestedAt(),
                approval.getDecidedAt(),
                approval.getVersion()
        );
    }
}
