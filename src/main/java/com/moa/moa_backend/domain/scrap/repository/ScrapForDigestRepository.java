package com.moa.moa_backend.domain.scrap.repository;

import com.moa.moa_backend.domain.scrap.entity.Scrap;
import com.moa.moa_backend.domain.scrap.repository.projection.ScrapForDigestView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScrapForDigestRepository extends Repository<Scrap, Long> {

    @Query("""
        select new com.moa.moa_backend.domain.scrap.repository.projection.ScrapForDigestView(
            s.id, s.subtitle, s.memo, s.rawHtml, s.capturedAt
        )
        from Scrap s
        where s.userId = :userId
          and s.projectId = :projectId
          and s.stage = :stage
        order by s.capturedAt desc, s.id desc
    """)
    List<ScrapForDigestView> findRecentForDigest(
            @Param("userId") Long userId,
            @Param("projectId") Long projectId,
            @Param("stage") String stage,
            Pageable pageable
    );
}
