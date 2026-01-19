package com.moa.moa_backend.domain.scrap.dto;

import java.time.Instant;

public record ScrapDetailResponse(
        Long scrapId,
        Long projectId,
        String stage,
        String subtitle,
        String memo,
        String rawHtml,
        String aiSource,
        String aiSourceUrl,
        Instant capturedAt
) {}
