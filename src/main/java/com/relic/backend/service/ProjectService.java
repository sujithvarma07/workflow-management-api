package com.relic.backend.service;

import com.relic.backend.dto.ProjectMemberRequest;
import com.relic.backend.dto.ProjectMemberResponse;
import com.relic.backend.dto.ProjectRequest;
import com.relic.backend.dto.ProjectResponse;
import com.relic.backend.entity.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProjectService {

    ProjectResponse create(ProjectRequest request);

    Page<ProjectResponse> getAll(ProjectStatus status, Pageable pageable);

    ProjectResponse getById(Long id);

    ProjectResponse update(Long id, ProjectRequest request);

    void delete(Long id);

    ProjectMemberResponse addMember(Long projectId, ProjectMemberRequest request);

    List<ProjectMemberResponse> getMembers(Long projectId);

    void removeMember(Long projectId, Long userId);
}
