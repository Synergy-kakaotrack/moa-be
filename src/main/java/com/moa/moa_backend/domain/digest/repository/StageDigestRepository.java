package com.moa.moa_backend.domain.digest.repository;

import com.moa.moa_backend.domain.digest.entity.StageDigest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * stage_digests 테이블 접근 레포지토리
 * - (user_id, project_id, stage) 유니크이므로 해당 조합으로 1건 조회
 */
public interface StageDigestRepository extends JpaRepository<StageDigest, Long> {

    /**
     * 특정 사용자/프로젝트/작업단계의 요약 1건 조회
     */
    Optional<StageDigest> findByUserIdAndProjectIdAndStage(Long userId, Long projectId, String stage);
}

