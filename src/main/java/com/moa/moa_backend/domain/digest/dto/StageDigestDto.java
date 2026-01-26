package com.moa.moa_backend.domain.digest.dto;

import java.util.List;

/**
 * stage_digests.digest_json에 저장되는 "고정 스키마" DTO.
 * - overview: 필수
 * - decisions: 필수
 * - decisions[].title/decision/rationale: 필수
 * - alternatives/impact: 선택
 */
public record StageDigestDto(
        String overview,
        List<DecisionDto> decisions
) {
    public record DecisionDto(
            String title,
            String decision,
            String rationale,
            List<String> alternatives, // 선택 (mvp X)
            String impact               // 선택 (mvp X)
    ) {}
}
