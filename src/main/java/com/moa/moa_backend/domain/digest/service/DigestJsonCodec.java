package com.moa.moa_backend.domain.digest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moa.moa_backend.domain.digest.dto.StageDigestDto;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * digest_json을 저장/조회하기 위한 변환기.
 * - LLM 출력이 고정 스키마를 준수하는지 필수 필드 검증도 담당.
 */
@Component
public class DigestJsonCodec {

    private final ObjectMapper objectMapper;

    public DigestJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(StageDigestDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new IllegalStateException("digest json serialize failed", e);
        }
    }

    public StageDigestDto fromJson(String json) {
        try {
            return objectMapper.readValue(json, StageDigestDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("digest json parse failed", e);
        }
    }

    /**
     * MVP 필수 규칙 검증:
     * - overview 필수
     * - decisions 비어있으면 안 됨
     * - decisions[].title/decision/rationale 필수
     */
    public void validate(StageDigestDto dto) {
        if (dto == null) throw new IllegalArgumentException("digest is null");
        if (!StringUtils.hasText(dto.overview())) throw new IllegalArgumentException("overview is required");
        if (dto.decisions() == null || dto.decisions().isEmpty()) throw new IllegalArgumentException("decisions is required");

        for (StageDigestDto.DecisionDto d : dto.decisions()) {
            if (!StringUtils.hasText(d.title())) throw new IllegalArgumentException("decision.title is required");
            if (!StringUtils.hasText(d.decision())) throw new IllegalArgumentException("decision.decision is required");
            if (!StringUtils.hasText(d.rationale())) throw new IllegalArgumentException("decision.rationale is required");
        }
    }
}
