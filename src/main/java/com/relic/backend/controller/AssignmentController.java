package com.relic.backend.controller;

import com.relic.backend.dto.AssignmentRequest;
import com.relic.backend.dto.AssignmentResponse;
import com.relic.backend.service.AssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @PostMapping("/api/v1/tasks/{taskId}/assignments")
    public ResponseEntity<AssignmentResponse> assign(
            @PathVariable Long taskId, @Valid @RequestBody AssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentService.assign(taskId, request));
    }

    @GetMapping("/api/v1/tasks/{taskId}/assignments")
    public List<AssignmentResponse> getByTask(@PathVariable Long taskId) {
        return assignmentService.getByTask(taskId);
    }

    @DeleteMapping("/api/v1/assignments/{id}")
    public ResponseEntity<Void> unassign(@PathVariable Long id) {
        assignmentService.unassign(id);
        return ResponseEntity.noContent().build();
    }
}
