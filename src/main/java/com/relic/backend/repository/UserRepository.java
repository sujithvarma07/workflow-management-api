package com.relic.backend.repository;

import com.relic.backend.entity.User;
import com.relic.backend.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(UserRole role);

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByActivationToken(String activationToken);

    Optional<User> findByPasswordResetToken(String passwordResetToken);

    Page<User> findByRole(UserRole role, Pageable pageable);
}
