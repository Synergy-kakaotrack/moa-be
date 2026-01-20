package com.moa.moa_backend.domain.scrap.dto;

import java.time.Instant;
import java.util.List;

public record ScrapListResponse(
        List<Item> items,
        String nextCursor
) {
    public record Item(
            Long scrapId,
            Long projectId,
            String stage,
            String subtitle,
            String memo,
            Instant capturedAt
    ) {}
}
