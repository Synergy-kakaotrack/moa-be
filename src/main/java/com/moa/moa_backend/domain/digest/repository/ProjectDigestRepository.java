package com.moa.moa_backend.domain.digest.repository;

import com.moa.moa_backend.domain.digest.entity.ProjectDigest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectDigestRepository extends JpaRepository<ProjectDigest, Long> {

    Optional<ProjectDigest> findByUserIdAndProjectId(Long userId, Long projectId);

    boolean existsByUserIdAndProjectId(Long userId, Long projectId);
}
