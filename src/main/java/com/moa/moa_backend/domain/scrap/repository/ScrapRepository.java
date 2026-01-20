package com.moa.moa_backend.domain.scrap.repository;

import com.moa.moa_backend.domain.scrap.entity.Scrap;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ScrapRepository extends JpaRepository<Scrap, Long> {

    Optional<Scrap> findByIdAndUserId(Long scrapId, Long userId);

    Optional<Scrap> findFirstByUserIdOrderByCapturedAtDesc(Long userId);

    // =========================
    // List: 첫 페이지 (cursor 없음)
    // =========================
    @Query("""
        select s
        from Scrap s
        where s.userId = :userId
          and s.projectId = :projectId
          and s.stage = :stage
        order by s.capturedAt desc, s.id desc
    """)
    List<Scrap> findFirstPage(
            @Param("userId") Long userId,
            @Param("projectId") Long projectId,
            @Param("stage") String stage,
            Pageable pageable
    );

    // =========================
    // List: 다음 페이지 (cursor 있음)
    // =========================
    @Query("""
        select s
        from Scrap s
        where s.userId = :userId
          and s.projectId = :projectId
          and s.stage = :stage
          and (
              s.capturedAt < :lastCapturedAt
              or (s.capturedAt = :lastCapturedAt and s.id < :lastScrapId)
          )
        order by s.capturedAt desc, s.id desc
    """)
    List<Scrap> findNextPage(
            @Param("userId") Long userId,
            @Param("projectId") Long projectId,
            @Param("stage") String stage,
            @Param("lastCapturedAt") Instant lastCapturedAt,
            @Param("lastScrapId") Long lastScrapId,
            Pageable pageable
    );

    // =========================
    // Recent context (native)
    // =========================
    @Query(value = """
        select x.project_id   as projectId,
               x.project_name as projectName,
               x.stage        as lastStage,
               x.captured_at  as lastCapturedAt
        from (
            select distinct on (s.project_id)
                   s.project_id,
                   p.project_name,
                   s.stage,
                   s.captured_at,
                   s.scrap_id
            from scraps s
            join projects p on p.project_id = s.project_id
            where s.user_id = :userId
            order by s.project_id, s.captured_at desc, s.scrap_id desc
        ) x
        order by x.captured_at desc
        limit 3
    """, nativeQuery = true)
    List<RecentContextRow> findRecentContext(@Param("userId") Long userId);

    interface RecentContextRow {
        Long getProjectId();
        String getProjectName();
        String getLastStage();
        Instant getLastCapturedAt();
    }
}
