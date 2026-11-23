package com.relic.backend.jobs.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectReportProcessor {

    private final ProjectReportJobService reportJobService;

    @Async("jobTaskExecutor")
    public void process(Long jobId, Long projectId) {
        reportJobService.markStarted(jobId);
        try {
            ProjectReportJobService.ReportResult result = reportJobService.compute(projectId);
            reportJobService.finishSuccess(jobId, result);
        } catch (Exception e) {
            log.warn("Project report job {} failed: {}", jobId, e.toString());
            reportJobService.finishFailure(jobId, e.getMessage());
        }
    }
}
