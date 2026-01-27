package com.moa.moa_backend.domain.digest.dto;

import java.time.OffsetDateTime;

/**
 * 프론트 응답용 DTO
 * - digest는 없을 수 있으므로 null 허용
 * - digest는 MARKDOWN TEXT
 */
public record StageDigestResponse(
        ProjectDto project,
        String stage,
        String digest,
        Meta meta
) {
    public record ProjectDto(Long projectId, String projectName) {}

    public record Meta(
            boolean exists,
            boolean outdated,
            OffsetDateTime sourceLastCapturedAt,
            OffsetDateTime latestScrapCapturedAt,
            OffsetDateTime updatedAt,
            int version
    ) {}
}

