package com.relic.backend.service;

import com.relic.backend.dto.TaskRequest;
import com.relic.backend.dto.TaskResponse;
import com.relic.backend.dto.TaskStatusUpdateRequest;
import com.relic.backend.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TaskService {

    TaskResponse create(TaskRequest request);

    Page<TaskResponse> getAll(Long projectId, TaskStatus status, Pageable pageable);

    TaskResponse getById(Long id);

    TaskResponse update(Long id, TaskRequest request);

    TaskResponse updateStatus(Long id, TaskStatusUpdateRequest request);

    void delete(Long id);
}
