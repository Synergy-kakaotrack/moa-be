package com.moa.moa_backend.domain.scrap.repository;

import com.moa.moa_backend.domain.scrap.entity.Scrap;
import com.moa.moa_backend.domain.scrap.repository.projection.DigestRefreshTarget;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ScrapDigestQueryRepository extends Repository<Scrap, Long> {

    @Query("""
        select max(s.capturedAt)
        from Scrap s
        where s.userId = :userId
          and s.projectId = :projectId
          and s.stage = :stage
    """)
    Instant findLatestCapturedAt(
            @Param("userId") Long userId,
            @Param("projectId") Long projectId,
            @Param("stage") String stage
    );

    /**
     * 스케줄러용: 최근 활동한 (userId, projectId, stage) 목록 조회
     * - since 이후 scraps만 대상
     * - 각 그룹의 최신 captured_at 기준으로 최신순 정렬
     * - limit 개수 제한
     *
     * NOTE: 테이블/컬럼명이 실제 DB와 다르면 여기 nativeQuery를 맞춰줘야 함.
     * (예: table = scraps, column = captured_at/user_id/project_id/stage)
     */
    @Query(value = """
        select
            s.user_id as userId,
            s.project_id as projectId,
            s.stage as stage
        from scraps s
        where s.captured_at >= :since
        group by s.user_id, s.project_id, s.stage
        order by max(s.captured_at) desc
        limit :limit
        """, nativeQuery = true)
    List<DigestRefreshTarget> findRecentTargetsForAutoRefresh(
            @Param("since") Instant since,
            @Param("limit") int limit
    );

}
