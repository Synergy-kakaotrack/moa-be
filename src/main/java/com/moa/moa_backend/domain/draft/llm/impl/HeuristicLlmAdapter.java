package com.moa.moa_backend.domain.draft.llm.impl;

import com.moa.moa_backend.domain.draft.dto.DraftRecommendCommand;
import com.moa.moa_backend.domain.draft.dto.DraftRecommendation;
import com.moa.moa_backend.domain.draft.entity.DraftStage;
import com.moa.moa_backend.domain.draft.entity.RecMethod;
import com.moa.moa_backend.domain.draft.llm.LlmRecommendationPort;
import org.springframework.stereotype.Component;

/*
 * LLM 장애 시 폴백 구현 기반 - 휴리스틱 어댑터
 */
@Component
public class HeuristicLlmAdapter implements LlmRecommendationPort {

    @Override
    public DraftRecommendation recommend(DraftRecommendCommand command) {

        Long projectId = null;

        //TODO 최근 컨텍스트가 있고 프로젝트가 있으면 우선 사용
        if(command.recentContext() != null){
            projectId = command.recentContext().projectId();
        }

        //TODO 없으면 프로젝트 목록에서 선택
        if(projectId ==null && command.projects() != null && !command.projects().isEmpty()){
            projectId = command.projects().get(0).projectId();
        }


        //TODO stage는 항상 FIXED_STAGES에서만
        String stage;
        if(command.recentContext() != null && DraftStage.isValid(command.recentContext().stage())){
            stage = command.recentContext().stage();
        } else {
            stage = command.fixedStages().get(0)
        }

        String subtitle = makeSubtitle(command.contentPlain());

        return new DraftRecommendation(
                projectId,
                stage,
                subtitle,
                RecMethod.FALLBACK_RECENT
        );
    }

    private String makeSubtitle(String content) {
        if (content == null || content.isBlank()) {
            return "임시 저장";
        }
        String trimmed = content.trim();
        return trimmed.length() <= 30 ? trimmed : trimmed.substring(0, 30);
    }
}

