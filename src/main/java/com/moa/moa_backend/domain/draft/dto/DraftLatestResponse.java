package com.moa.moa_backend.domain.draft.dto;

import java.time.Instant;

public record DraftLatestResponse(
        Long draftId,
        Long projectId,
        String stage,
        String subtitle,
        Instant expiresAt
) {}
