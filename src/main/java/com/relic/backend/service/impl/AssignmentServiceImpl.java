package com.relic.backend.service.impl;

import com.relic.backend.dto.AssignmentRequest;
import com.relic.backend.dto.AssignmentResponse;
import com.relic.backend.entity.Assignment;
import com.relic.backend.entity.Task;
import com.relic.backend.entity.User;
import com.relic.backend.exception.DuplicateResourceException;
import com.relic.backend.exception.ResourceNotFoundException;
import com.relic.backend.repository.AssignmentRepository;
import com.relic.backend.repository.TaskRepository;
import com.relic.backend.repository.UserRepository;
import com.relic.backend.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssignmentServiceImpl implements AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AssignmentResponse assign(Long taskId, AssignmentRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id " + taskId));
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + request.userId()));

        if (assignmentRepository.existsByTaskIdAndUserIdAndActiveTrue(taskId, request.userId())) {
            throw new DuplicateResourceException(
                    "User " + request.userId() + " is already actively assigned to task " + taskId);
        }

        Assignment assignment = Assignment.builder()
                .task(task)
                .user(user)
                .build();

        return toResponse(assignmentRepository.save(assignment));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentResponse> getByTask(Long taskId) {
        if (!taskRepository.existsById(taskId)) {
            throw new ResourceNotFoundException("Task not found with id " + taskId);
        }
        return assignmentRepository.findByTaskId(taskId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void unassign(Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assignment not found with id " + assignmentId));

        assignment.setActive(false);
        assignment.setUnassignedAt(Instant.now());
        assignmentRepository.save(assignment);
    }

    private AssignmentResponse toResponse(Assignment assignment) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getTask().getId(),
                assignment.getTask().getTitle(),
                assignment.getUser().getId(),
                assignment.getUser().getUsername(),
                assignment.getAssignedAt(),
                assignment.getUnassignedAt(),
                assignment.isActive()
        );
    }
}
