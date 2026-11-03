package com.relic.backend.repository;

import com.relic.backend.entity.Project;
import com.relic.backend.entity.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsByNameIgnoreCase(String name);

    Page<Project> findByStatus(ProjectStatus status, Pageable pageable);
}
