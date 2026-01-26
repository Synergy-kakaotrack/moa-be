package com.moa.moa_backend.domain.digest.dto;

import java.time.OffsetDateTime;

/**
 * 프론트 응답용 DTO
 * - digest는 없을 수 있으므로 null 허용
 */
public record StageDigestResponse(
        ProjectDto project,
        String stage,
        StageDigestDto digest, // null 가능
        Meta meta
) {
    public record ProjectDto(Long projectId, String projectName) {}

    /**
     * meta:
     * - exists: digest가 저장되어 있는지
     * - outdated: 최신 스크랩이 digest 기준시각보다 더 최신인지
     */
    public record Meta(
            boolean exists,
            boolean outdated,
            OffsetDateTime sourceLastCapturedAt,
            OffsetDateTime latestScrapCapturedAt,
            OffsetDateTime updatedAt
    ) {}
}
