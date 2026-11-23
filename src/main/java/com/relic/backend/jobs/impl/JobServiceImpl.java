package com.relic.backend.jobs.impl;

import com.relic.backend.dto.BulkTaskImportRequest;
import com.relic.backend.dto.JobResponse;
import com.relic.backend.dto.ProjectReportRequest;
import com.relic.backend.entity.Job;
import com.relic.backend.entity.JobStatus;
import com.relic.backend.entity.JobType;
import com.relic.backend.exception.ResourceNotFoundException;
import com.relic.backend.jobs.JobService;
import com.relic.backend.repository.JobRepository;
import com.relic.backend.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private final JobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final BulkTaskImportProcessor bulkTaskImportProcessor;
    private final ProjectReportProcessor projectReportProcessor;

    @Override
    @Transactional
    public JobResponse submitBulkTaskImport(BulkTaskImportRequest request, String username) {
        if (!projectRepository.existsById(request.projectId())) {
            throw new ResourceNotFoundException("Project not found with id " + request.projectId());
        }

        Job job = jobRepository.save(Job.builder()
                .type(JobType.BULK_TASK_IMPORT)
                .status(JobStatus.QUEUED)
                .createdBy(username)
                .totalItems(request.items().size())
                .build());

        // Submitted only after this transaction (which just committed the
        // QUEUED job row) - the @Async call itself runs on a different
        // thread regardless, but there's no reason to kick it off before
        // the row the processor will look up by id is actually visible.
        bulkTaskImportProcessor.process(job.getId(), request.projectId(), request.items());

        return toResponse(jobRepository.findById(job.getId()).orElseThrow());
    }

    @Override
    @Transactional
    public JobResponse submitProjectReport(ProjectReportRequest request, String username) {
        if (!projectRepository.existsById(request.projectId())) {
            throw new ResourceNotFoundException("Project not found with id " + request.projectId());
        }

        Job job = jobRepository.save(Job.builder()
                .type(JobType.PROJECT_REPORT)
                .status(JobStatus.QUEUED)
                .createdBy(username)
                .build());

        projectReportProcessor.process(job.getId(), request.projectId());

        return toResponse(jobRepository.findById(job.getId()).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public JobResponse getById(Long id) {
        return jobRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JobResponse> getAll(JobType type, JobStatus status, Pageable pageable) {
        Page<Job> page;
        if (type != null && status != null) {
            page = jobRepository.findByTypeAndStatus(type, status, pageable);
        } else if (type != null) {
            page = jobRepository.findByType(type, pageable);
        } else if (status != null) {
            page = jobRepository.findByStatus(status, pageable);
        } else {
            page = jobRepository.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    private JobResponse toResponse(Job job) {
        return new JobResponse(
                job.getId(), job.getType(), job.getStatus(), job.getCreatedBy(),
                job.getCreatedAt(), job.getStartedAt(), job.getFinishedAt(),
                job.getTotalItems(), job.getSuccessCount(), job.getFailureCount(),
                job.getResultSummary(), job.getErrorDetails());
    }
}
