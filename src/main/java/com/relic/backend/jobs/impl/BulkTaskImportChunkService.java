package com.relic.backend.jobs.impl;

import com.relic.backend.dto.BulkTaskImportItem;
import com.relic.backend.dto.TaskRequest;
import com.relic.backend.entity.Job;
import com.relic.backend.entity.JobStatus;
import com.relic.backend.repository.JobRepository;
import com.relic.backend.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The REQUIRES_NEW-transactional steps of a bulk import, kept in a separate
 * bean from BulkTaskImportProcessor deliberately: @Transactional (like
 * @Async) only takes effect through Spring's proxy, so calling these methods
 * from another method in the SAME class (a self-invocation, e.g.
 * this.processChunk(...)) would silently run with no transaction at all.
 * Being in a different bean forces every call here to go through the proxy.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulkTaskImportChunkService {

    private final JobRepository jobRepository;
    private final TaskService taskService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStarted(Long jobId) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(JobStatus.IN_PROGRESS);
        job.setStartedAt(Instant.now());
        jobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChunkResult processChunk(Long projectId, List<BulkTaskImportItem> chunk, int chunkStartIndex) {
        int successCount = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < chunk.size(); i++) {
            BulkTaskImportItem item = chunk.get(i);
            int overallIndex = chunkStartIndex + i;
            try {
                taskService.create(new TaskRequest(
                        projectId, item.title(), item.description(), null, item.priority(), item.dueDate()));
                successCount++;
            } catch (Exception e) {
                errors.add("item " + overallIndex + " (\"" + safeTitle(item.title()) + "\"): " + e.getMessage());
            }
        }

        return new ChunkResult(successCount, errors);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(Long jobId, int totalItems, int successCount, List<String> errors) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        job.setTotalItems(totalItems);
        job.setSuccessCount(successCount);
        job.setFailureCount(errors.size());
        job.setFinishedAt(Instant.now());
        job.setStatus(errors.isEmpty() ? JobStatus.COMPLETED : JobStatus.COMPLETED_WITH_ERRORS);
        String joined = String.join("\n", errors.subList(0, Math.min(errors.size(), 200)));
        job.setErrorDetails(joined.isBlank() ? null : joined);
        jobRepository.save(job);

        log.info("Bulk task import job {} finished: {}/{} succeeded", jobId, successCount, totalItems);
    }

    private String safeTitle(String title) {
        return title == null ? "" : title.length() > 50 ? title.substring(0, 50) + "..." : title;
    }

    public record ChunkResult(int successCount, List<String> errors) {
    }
}
