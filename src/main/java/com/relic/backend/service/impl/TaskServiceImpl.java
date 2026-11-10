package com.relic.backend.service.impl;

import com.relic.backend.audit.AuditService;
import com.relic.backend.dto.TaskRequest;
import com.relic.backend.dto.TaskResponse;
import com.relic.backend.dto.TaskStatusUpdateRequest;
import com.relic.backend.entity.Project;
import com.relic.backend.entity.Task;
import com.relic.backend.entity.TaskStatus;
import com.relic.backend.exception.InvalidStateTransitionException;
import com.relic.backend.exception.ResourceNotFoundException;
import com.relic.backend.repository.ProjectRepository;
import com.relic.backend.repository.TaskRepository;
import com.relic.backend.security.SecurityUtils;
import com.relic.backend.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    // The allowed next states for each current task status. DONE and
    // CANCELLED are terminal (empty sets) - once a task reaches either, no
    // further transition is permitted.
    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = Map.of(
            TaskStatus.TODO, EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.CANCELLED),
            TaskStatus.IN_PROGRESS, EnumSet.of(TaskStatus.BLOCKED, TaskStatus.DONE, TaskStatus.CANCELLED),
            TaskStatus.BLOCKED, EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED),
            TaskStatus.DONE, EnumSet.noneOf(TaskStatus.class),
            TaskStatus.CANCELLED, EnumSet.noneOf(TaskStatus.class)
    );

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public TaskResponse create(TaskRequest request) {
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id " + request.projectId()));

        Task task = Task.builder()
                .project(project)
                .title(request.title())
                .description(request.description())
                .status(request.status())
                .priority(request.priority())
                .dueDate(request.dueDate())
                .build();

        return toResponse(taskRepository.save(task));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TaskResponse> getAll(Long projectId, TaskStatus status, Pageable pageable) {
        Page<Task> page;
        if (projectId != null && status != null) {
            page = taskRepository.findByProjectIdAndStatus(projectId, status, pageable);
        } else if (projectId != null) {
            page = taskRepository.findByProjectId(projectId, pageable);
        } else if (status != null) {
            page = taskRepository.findByStatus(status, pageable);
        } else {
            page = taskRepository.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse getById(Long id) {
        return toResponse(findTaskOrThrow(id));
    }

    @Override
    @Transactional
    public TaskResponse update(Long id, TaskRequest request) {
        Task task = findTaskOrThrow(id);

        if (!task.getProject().getId().equals(request.projectId())) {
            Project project = projectRepository.findById(request.projectId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Project not found with id " + request.projectId()));
            task.setProject(project);
        }

        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setDueDate(request.dueDate());
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.status() != null) {
            task.setStatus(request.status());
        }

        return toResponse(taskRepository.save(task));
    }

    @Override
    @Transactional
    public TaskResponse updateStatus(Long id, TaskStatusUpdateRequest request) {
        Task task = findTaskOrThrow(id);
        TaskStatus current = task.getStatus();
        TaskStatus target = request.status();

        if (current == target) {
            return toResponse(task);
        }

        Set<TaskStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidStateTransitionException(
                    "Cannot transition task from " + current + " to " + target);
        }

        task.setStatus(target);
        TaskResponse response = toResponse(taskRepository.save(task));

        auditService.success(SecurityUtils.currentUsername(), "TASK_STATUS_CHANGED", "Task",
                String.valueOf(id), current + " -> " + target);

        return response;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Task task = findTaskOrThrow(id);
        taskRepository.delete(task);
    }

    private Task findTaskOrThrow(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id " + id));
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getProject().getId(),
                task.getProject().getName(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                task.getCreatedAt(),
                task.getVersion()
        );
    }
}
