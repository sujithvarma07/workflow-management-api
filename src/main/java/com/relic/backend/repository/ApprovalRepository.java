package com.relic.backend.repository;

import com.relic.backend.entity.Approval;
import com.relic.backend.entity.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {

    Page<Approval> findByStatus(ApprovalStatus status, Pageable pageable);

    Page<Approval> findByTaskId(Long taskId, Pageable pageable);

    Page<Approval> findByTaskIdAndStatus(Long taskId, ApprovalStatus status, Pageable pageable);

    Optional<Approval> findByTaskIdAndStatus(Long taskId, ApprovalStatus status);

    boolean existsByTaskIdAndStatus(Long taskId, ApprovalStatus status);
}
