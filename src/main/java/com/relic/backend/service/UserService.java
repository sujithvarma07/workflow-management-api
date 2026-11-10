package com.relic.backend.service;

import com.relic.backend.dto.UserCreateRequest;
import com.relic.backend.dto.UserResponse;
import com.relic.backend.dto.UserStatusUpdateRequest;
import com.relic.backend.dto.UserUpdateRequest;
import com.relic.backend.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserResponse create(UserCreateRequest request);

    Page<UserResponse> getAll(UserRole role, Pageable pageable);

    UserResponse getById(Long id);

    UserResponse getByUsername(String username);

    UserResponse update(Long id, UserUpdateRequest request);

    UserResponse updateStatus(Long id, UserStatusUpdateRequest request);

    void delete(Long id);
}
