package com.relic.backend.repository;

import com.relic.backend.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findByTaskId(Long taskId);

    Optional<Assignment> findByTaskIdAndUserIdAndActiveTrue(Long taskId, Long userId);

    boolean existsByTaskIdAndUserIdAndActiveTrue(Long taskId, Long userId);
}
