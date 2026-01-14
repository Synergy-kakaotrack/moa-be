package com.moa.moa_backend.domain.draft.llm.impl;

import com.moa.moa_backend.domain.draft.dto.DraftRecommendCommand;
import com.moa.moa_backend.domain.draft.dto.DraftRecommendation;
import com.moa.moa_backend.domain.draft.entity.DraftStage;
import com.moa.moa_backend.domain.draft.entity.RecMethod;
import com.moa.moa_backend.domain.draft.llm.LlmRecommendationPort;
import org.springframework.stereotype.Component;

/*
 * LLM 장애 시 폴백 구현 기반 - 휴리스틱 어댑터
 * - LLM 실패 시 subtitle은 항상 null
 * - 프로젝트가 없으면(N/A) recMethod = NONE
 * - 프로젝트가 있으면 recMethod = FALLBACK_RECENT
 */
@Component
public class HeuristicLlmAdapter implements LlmRecommendationPort {

    @Override
    public DraftRecommendation recommend(DraftRecommendCommand command) {

        Long projectId = null;

        // 최근 컨텍스트가 있고(projectId가 있다면) 우선 사용
        if (command.recentContext() != null) {
            projectId = command.recentContext().projectId();
        }

        // 없으면 프로젝트 목록에서 선택
        if (projectId == null && command.projects() != null && !command.projects().isEmpty()) {
            projectId = command.projects().get(0).projectId();
        }

        // stage는 항상 FIXED_STAGES 범위 내에서만
        String stage;
        if (command.recentContext() != null && DraftStage.isValid(command.recentContext().stage())) {
            stage = command.recentContext().stage();
        } else {
            stage = command.fixedStages().get(0);
        }

        // LLM 실패 -> subtitle은 항상 null
        RecMethod method = (projectId == null) ? RecMethod.NONE : RecMethod.FALLBACK_RECENT;

        return new DraftRecommendation(
                projectId,
                stage,
                null,
                method
        );
    }
}

