package com.relic.backend.security;

import com.relic.backend.repository.AssignmentRepository;
import com.relic.backend.repository.ProjectMemberRepository;
import com.relic.backend.repository.UserRepository;
import com.relic.backend.entity.ProjectMemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Ownership-based authorization checks, referenced from @PreAuthorize SpEL
 * expressions as the "authz" bean (e.g. "@authz.isTaskAssignee(#id,
 * authentication.name)"). Kept separate from role checks (which SpEL
 * expresses directly with hasRole/hasAnyRole) because "does this specific
 * user own/manage this specific record" always needs a database lookup,
 * while role checks don't.
 */
@Component("authz")
@RequiredArgsConstructor
public class AuthorizationService {

    private final AssignmentRepository assignmentRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;

    public boolean isTaskAssignee(Long taskId, String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .map(user -> assignmentRepository.existsByTaskIdAndUserIdAndActiveTrue(taskId, user.getId()))
                .orElse(false);
    }

    public boolean isProjectOwner(Long projectId, String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .flatMap(user -> projectMemberRepository.findByProjectIdAndUserId(projectId, user.getId()))
                .map(member -> member.getRoleInProject() == ProjectMemberRole.OWNER)
                .orElse(false);
    }
}
