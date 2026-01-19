package com.moa.moa_backend.domain.scrap.dto;

import java.time.Instant;
import java.util.List;

public record ScrapRecentContextResponse(
        List<Item> items
) {
    public record Item(
            Long projectId,
            String projectName,
            String lastStage,
            Instant lastCapturedAt
    ) {}
}
