package com.moa.moa_backend.domain.scrap.repository;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * LLM 입력용으로 필요한 스크랩 필드만 추려온 Projection.
 * - memo는 선택사항이므로 null 가능.
 * - rawHtml은 크기 제한/전처리를 거쳐 LLM 입력에 포함한다.
 */
public record ScrapForDigestView(
        Long scrapId,
        String subtitle,
        String memo,
        String rawHtml,
        Instant capturedAt
) {}
