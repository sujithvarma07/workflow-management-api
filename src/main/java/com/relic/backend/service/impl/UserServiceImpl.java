package com.relic.backend.service.impl;

import com.relic.backend.audit.AuditService;
import com.relic.backend.dto.UserCreateRequest;
import com.relic.backend.dto.UserResponse;
import com.relic.backend.dto.UserStatusUpdateRequest;
import com.relic.backend.dto.UserUpdateRequest;
import com.relic.backend.entity.User;
import com.relic.backend.entity.UserRole;
import com.relic.backend.entity.UserStatus;
import com.relic.backend.exception.DuplicateResourceException;
import com.relic.backend.exception.ResourceNotFoundException;
import com.relic.backend.repository.UserRepository;
import com.relic.backend.security.RefreshTokenService;
import com.relic.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    @Override
    @Transactional
    public UserResponse create(UserCreateRequest request) {
        // Username uniqueness is already enforced by the @UniqueUsername
        // validator at the DTO level; the email check stays here because it
        // is not exposed as a bean-validation annotation.
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("A user with email '" + request.email() + "' already exists");
        }

        // Created directly by an ADMIN, so it's active immediately -
        // there's no activation email round trip to go through, unlike
        // self-registration via POST /api/v1/auth/register.
        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .fullName(request.fullName())
                .role(request.role())
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.ACTIVE)
                .build();

        User saved = userRepository.save(user);
        auditService.success(com.relic.backend.security.SecurityUtils.currentUsername(),
                "USER_CREATED", "User", String.valueOf(saved.getId()), "role=" + saved.getRole());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAll(UserRole role, Pageable pageable) {
        Page<User> page = (role != null)
                ? userRepository.findByRole(role, pageable)
                : userRepository.findAll(pageable);
        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return toResponse(findUserOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username " + username));
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = findUserOrThrow(id);

        if (!user.getEmail().equalsIgnoreCase(request.email())
                && userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("A user with email '" + request.email() + "' already exists");
        }

        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setRole(request.role());

        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse updateStatus(Long id, UserStatusUpdateRequest request) {
        User user = findUserOrThrow(id);
        UserStatus previous = user.getStatus();
        user.setStatus(request.status());
        User saved = userRepository.save(user);

        // Suspending a user should also kill any refresh token they're
        // currently holding - otherwise a suspended account could keep
        // minting new short-lived access tokens via /auth/refresh until
        // its existing refresh token happened to expire on its own.
        if (request.status() == UserStatus.SUSPENDED) {
            refreshTokenService.revokeAllForUser(user.getId());
        }

        auditService.success(com.relic.backend.security.SecurityUtils.currentUsername(),
                "USER_STATUS_CHANGED", "User", String.valueOf(id),
                previous + " -> " + request.status());

        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        User user = findUserOrThrow(id);
        userRepository.delete(user);
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + id));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }
}
