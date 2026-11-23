package com.relic.backend.repository;

import com.relic.backend.entity.Job;
import com.relic.backend.entity.JobStatus;
import com.relic.backend.entity.JobType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {

    Page<Job> findByType(JobType type, Pageable pageable);

    Page<Job> findByStatus(JobStatus status, Pageable pageable);

    Page<Job> findByTypeAndStatus(JobType type, JobStatus status, Pageable pageable);
}
