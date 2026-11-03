package com.relic.backend.service;

import com.relic.backend.dto.AssignmentRequest;
import com.relic.backend.dto.AssignmentResponse;

import java.util.List;

public interface AssignmentService {

    AssignmentResponse assign(Long taskId, AssignmentRequest request);

    List<AssignmentResponse> getByTask(Long taskId);

    void unassign(Long assignmentId);
}
