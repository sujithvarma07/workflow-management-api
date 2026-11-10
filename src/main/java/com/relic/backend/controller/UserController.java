package com.relic.backend.controller;

import com.relic.backend.dto.UserCreateRequest;
import com.relic.backend.dto.UserResponse;
import com.relic.backend.dto.UserStatusUpdateRequest;
import com.relic.backend.dto.UserUpdateRequest;
import com.relic.backend.entity.UserRole;
import com.relic.backend.security.SecurityUtils;
import com.relic.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// User management is ADMIN-only across the board, except GET /me, which
// lets any authenticated user see their own profile - everyone is allowed
// to know who they are, that's not a privilege.
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserResponse> getAll(
            @RequestParam(required = false) UserRole role,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return userService.getAll(role, pageable);
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser() {
        return userService.getByUsername(SecurityUtils.currentUsername());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse getById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return userService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusUpdateRequest request) {
        return userService.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
