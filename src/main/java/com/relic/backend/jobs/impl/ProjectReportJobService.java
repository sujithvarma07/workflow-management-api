package com.relic.backend.jobs.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relic.backend.entity.Job;
import com.relic.backend.entity.JobStatus;
import com.relic.backend.entity.Project;
import com.relic.backend.entity.TaskStatus;
import com.relic.backend.exception.ResourceNotFoundException;
import com.relic.backend.repository.JobRepository;
import com.relic.backend.repository.ProjectMemberRepository;
import com.relic.backend.repository.ProjectRepository;
import com.relic.backend.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The transactional steps of a project report run, in a separate bean from
 * ProjectReportProcessor for the same reason as BulkTaskImportChunkService:
 * @Transactional only takes effect through the Spring proxy, so these
 * methods must be called from a different bean, never self-invoked.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectReportJobService {

    private final JobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStarted(Long jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(JobStatus.IN_PROGRESS);
        job.setStartedAt(Instant.now());
        jobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ReportResult compute(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id " + projectId));

        Map<TaskStatus, Long> tasksByStatus = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) {
            tasksByStatus.put(status, taskRepository.countByProjectIdAndStatus(projectId, status));
        }

        long overdueCount = taskRepository.countByProjectIdAndDueDateBeforeAndStatusNotIn(
                projectId, LocalDate.now(), Set.of(TaskStatus.DONE, TaskStatus.CANCELLED));

        long totalTasks = taskRepository.countByProjectId(projectId);
        int memberCount = projectMemberRepository.findByProjectId(projectId).size();

        return new ReportResult(project.getName(), totalTasks, tasksByStatus, overdueCount, memberCount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishSuccess(Long jobId, ReportResult result) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        job.setTotalItems(1);
        job.setSuccessCount(1);
        job.setFailureCount(0);
        job.setFinishedAt(Instant.now());
        job.setStatus(JobStatus.COMPLETED);
        job.setResultSummary(toJson(result));
        jobRepository.save(job);
        log.info("Project report job {} completed for project '{}'", jobId, result.projectName());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishFailure(Long jobId, String errorMessage) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        job.setTotalItems(1);
        job.setSuccessCount(0);
        job.setFailureCount(1);
        job.setFinishedAt(Instant.now());
        job.setStatus(JobStatus.FAILED);
        job.setErrorDetails(errorMessage);
        jobRepository.save(job);
        log.warn("Project report job {} failed: {}", jobId, errorMessage);
    }

    private String toJson(ReportResult result) {
        try {
            // Enum-keyed maps don't serialize predictably with plain
            // ObjectMapper.writeValueAsString in every Jackson config, so
            // the status breakdown is converted to a String-keyed map first.
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("projectName", result.projectName());
            payload.put("totalTasks", result.totalTasks());
            Map<String, Long> statusBreakdown = new LinkedHashMap<>();
            result.tasksByStatus().forEach((status, count) -> statusBreakdown.put(status.name(), count));
            payload.put("tasksByStatus", statusBreakdown);
            payload.put("overdueTaskCount", result.overdueTaskCount());
            payload.put("memberCount", result.memberCount());
            payload.put("generatedAt", Instant.now().toString());
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"error\":\"failed to serialize report result\"}";
        }
    }

    public record ReportResult(
            String projectName, long totalTasks, Map<TaskStatus, Long> tasksByStatus,
            long overdueTaskCount, int memberCount) {
    }
}
