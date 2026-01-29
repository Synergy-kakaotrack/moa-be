package com.moa.moa_backend.domain.digest.dto;

import com.moa.moa_backend.domain.digest.entity.DigestKind;

import java.time.OffsetDateTime;

public record ProjectDigestResponse(
        ProjectDto project,
        DigestKind digestKind,   // DEFAULT | CUSTOM
        String digest,           // markdown, nullable
        Meta meta
) {
    public record ProjectDto(Long projectId, String projectName) {}

    public record Meta(
            boolean exists,
            boolean outdated,
            OffsetDateTime sourceLastCapturedAt,
            OffsetDateTime latestScrapCapturedAt,
            OffsetDateTime updatedAt,
            int version,
            Refresh refresh
    ) {}

    public record Refresh(
            String status,              // SUCCESS | FAILED | SKIPPED
            String errorCode,           // NOT_OUTDATED | NO_SCRAPS | RATE_LIMITED | PROVIDER_ERROR ...
            String message,
            Integer retryAfterSeconds,
            OffsetDateTime attemptedAt
    ) {}
}


