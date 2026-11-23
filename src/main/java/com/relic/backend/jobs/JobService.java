package com.relic.backend.jobs;

import com.relic.backend.dto.BulkTaskImportRequest;
import com.relic.backend.dto.JobResponse;
import com.relic.backend.dto.ProjectReportRequest;
import com.relic.backend.entity.JobStatus;
import com.relic.backend.entity.JobType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface JobService {

    JobResponse submitBulkTaskImport(BulkTaskImportRequest request, String username);

    JobResponse submitProjectReport(ProjectReportRequest request, String username);

    JobResponse getById(Long id);

    Page<JobResponse> getAll(JobType type, JobStatus status, Pageable pageable);
}
