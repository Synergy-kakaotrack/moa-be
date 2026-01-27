package com.moa.moa_backend.domain.scrap.repository.projection;

public record DigestRefreshTarget (
    Long userId,
    Long projectId,
    String stage
) {}


