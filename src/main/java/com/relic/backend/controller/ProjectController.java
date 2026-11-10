package com.relic.backend.controller;

import com.relic.backend.dto.ProjectMemberRequest;
import com.relic.backend.dto.ProjectMemberResponse;
import com.relic.backend.dto.ProjectRequest;
import com.relic.backend.dto.ProjectResponse;
import com.relic.backend.entity.ProjectStatus;
import com.relic.backend.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(request));
    }

    @GetMapping
    public Page<ProjectResponse> getAll(
            @RequestParam(required = false) ProjectStatus status,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return projectService.getAll(status, pageable);
    }

    @GetMapping("/{id}")
    public ProjectResponse getById(@PathVariable Long id) {
        return projectService.getById(id);
    }

    // Any ADMIN may edit any project; a MANAGER may only edit a project
    // they are the OWNER of - the ownership check (@authz.isProjectOwner)
    // requires a database lookup, unlike the role check, which is why it's
    // a separate bean reference rather than another hasRole(...) clause.
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MANAGER') and @authz.isProjectOwner(#id, authentication.name))")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return projectService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MANAGER') and @authz.isProjectOwner(#id, authentication.name))")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MANAGER') and @authz.isProjectOwner(#id, authentication.name))")
    public ResponseEntity<ProjectMemberResponse> addMember(
            @PathVariable Long id, @Valid @RequestBody ProjectMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.addMember(id, request));
    }

    @GetMapping("/{id}/members")
    public List<ProjectMemberResponse> getMembers(@PathVariable Long id) {
        return projectService.getMembers(id);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('MANAGER') and @authz.isProjectOwner(#id, authentication.name))")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        projectService.removeMember(id, userId);
        return ResponseEntity.noContent().build();
    }
}
