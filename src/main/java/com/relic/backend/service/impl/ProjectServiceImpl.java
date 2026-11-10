package com.relic.backend.service.impl;

import com.relic.backend.dto.ProjectMemberRequest;
import com.relic.backend.dto.ProjectMemberResponse;
import com.relic.backend.dto.ProjectRequest;
import com.relic.backend.dto.ProjectResponse;
import com.relic.backend.audit.AuditService;
import com.relic.backend.entity.Project;
import com.relic.backend.entity.ProjectMember;
import com.relic.backend.entity.ProjectMemberRole;
import com.relic.backend.entity.ProjectStatus;
import com.relic.backend.entity.User;
import com.relic.backend.exception.DuplicateResourceException;
import com.relic.backend.exception.ResourceNotFoundException;
import com.relic.backend.repository.ProjectMemberRepository;
import com.relic.backend.repository.ProjectRepository;
import com.relic.backend.repository.UserRepository;
import com.relic.backend.security.SecurityUtils;
import com.relic.backend.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Override
    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        if (projectRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("A project named '" + request.name() + "' already exists");
        }

        Project project = Project.builder()
                .name(request.name())
                .description(request.description())
                .status(request.status())
                .build();

        Project saved = projectRepository.save(project);

        // The creator is automatically added as the project's OWNER
        // member. Without this, a MANAGER who just created a project could
        // never manage its membership afterward - @PreAuthorize on
        // addMember/removeMember/update/delete requires either ADMIN or an
        // existing OWNER membership, and a brand-new project has no
        // members at all yet. An ADMIN creator is added too, for
        // visibility in the member list, even though ADMIN already
        // bypasses the ownership check everywhere else.
        String creatorUsername = SecurityUtils.currentUsername();
        if (creatorUsername != null) {
            userRepository.findByUsernameIgnoreCase(creatorUsername).ifPresent(creator -> {
                ProjectMember ownerMembership = ProjectMember.builder()
                        .project(saved)
                        .user(creator)
                        .roleInProject(ProjectMemberRole.OWNER)
                        .build();
                projectMemberRepository.save(ownerMembership);
            });
        }

        auditService.success(creatorUsername, "PROJECT_CREATED", "Project", String.valueOf(saved.getId()), null);

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProjectResponse> getAll(ProjectStatus status, Pageable pageable) {
        Page<Project> page = (status != null)
                ? projectRepository.findByStatus(status, pageable)
                : projectRepository.findAll(pageable);
        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse getById(Long id) {
        return toResponse(findProjectOrThrow(id));
    }

    @Override
    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request) {
        Project project = findProjectOrThrow(id);

        if (!project.getName().equalsIgnoreCase(request.name())
                && projectRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("A project named '" + request.name() + "' already exists");
        }

        project.setName(request.name());
        project.setDescription(request.description());
        if (request.status() != null) {
            project.setStatus(request.status());
        }

        // Relies on @Version on Project: if another request updated this
        // same row between our read and this write, Hibernate raises
        // ObjectOptimisticLockingFailureException instead of silently
        // overwriting the other change, and GlobalExceptionHandler turns
        // that into a 409 the client can retry.
        return toResponse(projectRepository.save(project));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Project project = findProjectOrThrow(id);
        projectRepository.delete(project);
    }

    @Override
    @Transactional
    public ProjectMemberResponse addMember(Long projectId, ProjectMemberRequest request) {
        Project project = findProjectOrThrow(projectId);
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + request.userId()));

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, request.userId())) {
            throw new DuplicateResourceException(
                    "User " + request.userId() + " is already a member of project " + projectId);
        }

        ProjectMember member = ProjectMember.builder()
                .project(project)
                .user(user)
                .roleInProject(request.roleInProject())
                .build();

        return toMemberResponse(projectMemberRepository.save(member));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getMembers(Long projectId) {
        findProjectOrThrow(projectId);
        return projectMemberRepository.findByProjectId(projectId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    @Override
    @Transactional
    public void removeMember(Long projectId, Long userId) {
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User " + userId + " is not a member of project " + projectId));
        projectMemberRepository.delete(member);
    }

    private Project findProjectOrThrow(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id " + id));
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getCreatedAt(),
                project.getVersion()
        );
    }

    private ProjectMemberResponse toMemberResponse(ProjectMember member) {
        return new ProjectMemberResponse(
                member.getId(),
                member.getProject().getId(),
                member.getUser().getId(),
                member.getUser().getUsername(),
                member.getRoleInProject(),
                member.getJoinedAt()
        );
    }
}
