package com.relic.backend.controller;

import com.relic.backend.dto.BulkTaskImportRequest;
import com.relic.backend.dto.JobResponse;
import com.relic.backend.dto.ProjectReportRequest;
import com.relic.backend.entity.JobStatus;
import com.relic.backend.entity.JobType;
import com.relic.backend.jobs.JobService;
import com.relic.backend.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping("/bulk-task-import")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<JobResponse> submitBulkTaskImport(@Valid @RequestBody BulkTaskImportRequest request) {
        JobResponse response = jobService.submitBulkTaskImport(request, SecurityUtils.currentUsername());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/project-reports")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<JobResponse> submitProjectReport(@Valid @RequestBody ProjectReportRequest request) {
        JobResponse response = jobService.submitProjectReport(request, SecurityUtils.currentUsername());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JobResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.getById(id));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<JobResponse>> getAll(
            @RequestParam(required = false) JobType type,
            @RequestParam(required = false) JobStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(jobService.getAll(type, status, pageable));
    }
}
