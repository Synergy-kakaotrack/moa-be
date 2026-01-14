package com.moa.moa_backend.domain.draft.dto;

public record DraftCreateResponse (
        Long draftId,
        Recommendation recommendation,
        String recMethod    //프론트가 처리하기 쉽게 enum그대로 내리지 않고, string으로 내림
){
    public record Recommendation (
            Long projectId,
            String stage,
            String subtitle
    ){}
}
