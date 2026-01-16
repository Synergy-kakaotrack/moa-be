package com.moa.moa_backend.domain.draft.repository;

import com.moa.moa_backend.domain.draft.entity.Draft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface DraftRepository extends JpaRepository<Draft, Long> {

    Optional<Draft> findByIdAndUserId(Long id, Long userId);

    long deleteByIdAndUserId(Long id, Long userId);

    Optional<Draft> findFirstByUserIdAndExpiredAtAfterOrderByCreatedAtDesc(Long userId, Instant now);
}
