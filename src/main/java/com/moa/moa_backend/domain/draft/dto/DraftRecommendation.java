package com.moa.moa_backend.domain.draft.dto;

import com.moa.moa_backend.domain.draft.entity.RecMethod;

//추천결과를 받아서 draft엔티티에 넣도록
public record DraftRecommendation (
        Long projectId,
        String stage,
        String subtitle,
        RecMethod recMethod
    ){
}
