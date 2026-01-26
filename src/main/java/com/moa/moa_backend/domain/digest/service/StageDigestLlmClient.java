package com.moa.moa_backend.domain.digest.service;

import com.moa.moa_backend.domain.digest.dto.StageDigestDto;
import com.moa.moa_backend.domain.scrap.repository.ScrapForDigestView;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MVP용 더미 LLM 클라이언트
 * - 일단 서버 부팅/라우팅/DB 저장 플로우를 먼저 검증하기 위함
 */
@Component
public class StageDigestLlmClient {

    public StageDigestDto generate(String projectName, String stage, List<ScrapForDigestView> scraps) {
        // TODO: Gemini 연동으로 교체
        // 임시로 고정 응답 반환(부팅 및 API 동작 확인용)
        return new StageDigestDto(
                stage + " 단계 요약(임시)",
                List.of(new StageDigestDto.DecisionDto(
                        "임시 결정",
                        "LLM 연동 전 더미 응답 사용",
                        "API 플로우/DB upsert 검증을 위해",
                        null,
                        null
                ))
        );
    }
}

