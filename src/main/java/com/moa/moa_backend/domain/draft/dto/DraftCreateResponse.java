package com.moa.moa_backend.domain.draft.dto;

import com.moa.moa_backend.domain.draft.entity.RecMethod;

public record DraftCreateResponse (
        Long draftId,
        Recommendation recommendation,
        RecMethod recMethod
){
    public record Recommendation (
            Long projectId,
            String stage,
            String subtitle
    ){}
}
