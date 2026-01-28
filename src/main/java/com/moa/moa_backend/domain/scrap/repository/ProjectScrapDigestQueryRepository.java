package com.moa.moa_backend.domain.scrap.repository;

import com.moa.moa_backend.domain.scrap.entity.Scrap;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ProjectScrapDigestQueryRepository extends Repository<Scrap, Long> {

    @Query("""
        select max(s.capturedAt)
        from Scrap s
        where s.userId = :userId
          and s.projectId = :projectId
    """)
    Instant findLatestCapturedAt(
            @Param("userId") Long userId,
            @Param("projectId") Long projectId
    );
}

