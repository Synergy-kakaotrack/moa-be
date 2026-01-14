package com.moa.moa_backend.domain.draft.llm.impl;

import com.moa.moa_backend.domain.draft.dto.DraftRecommendCommand;
import com.moa.moa_backend.domain.draft.dto.DraftRecommendation;
import com.moa.moa_backend.domain.draft.entity.DraftStage;
import com.moa.moa_backend.domain.draft.entity.RecMethod;
import com.moa.moa_backend.domain.draft.llm.LlmRecommendationPort;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Gemini 결과를 정책대로 정규화하고, 실패 시 휴리스틱으로 폴백한다.
 */
@Primary
@Component
public class GeminiLlmAdapter implements LlmRecommendationPort {

    private final GeminiClient geminiClient;
    private final HeuristicLlmAdapter heuristicLlmAdapter;

    public GeminiLlmAdapter(GeminiClient geminiClient, HeuristicLlmAdapter heuristicLlmAdapter) {
        this.geminiClient = geminiClient;
        this.heuristicLlmAdapter = heuristicLlmAdapter;
    }

    @Override
    public DraftRecommendation recommend(DraftRecommendCommand command) {

        // fixedStages가 비어있으면 서비스 자체가 성립이 애매하니, 최소 방어:
        if (command.fixedStages() == null || command.fixedStages().isEmpty()) {
            // 여기선 “LLM/휴리스틱” 둘 다 stage를 만들 수 없으니,
            // 운영 정책에 맞게 예외를 던지거나, 안전한 기본값을 하드코딩해야 함.
            // (추천: 서버 부팅 시점에 설정 검증)
            throw new IllegalStateException("fixedStages must not be empty");
        }

        try {
            GeminiClient.RecommendInput input = toLlmInput(command);

            // GeminiClient.recommend()는
            // - 타임아웃
            // - candidates 없음
            // - JSON 파싱 실패
            // 등을 'GeminiClientException'으로 던진다고 가정 (아래 설명)
            GeminiClient.LlmResult llm = geminiClient.recommend(input);

            // ---- LLM 성공 케이스 (1,2): recMethod = LLM ----
            Long projectId = normalizeProjectId(command, llm.projectId()); // 프로젝트 없으면 null 가능(케이스2)
            String stage = normalizeStage(command, llm.stage());           // 항상 fixedStages 내로
            String subtitle = normalizeSubtitle(llm.subtitle());           // LLM 성공이면 허용

            return new DraftRecommendation(projectId, stage, subtitle, RecMethod.LLM);

        } catch (GeminiClientException e) {
            // ---- LLM 실패 케이스 (3,4): 폴백 규칙 적용 ----
            return heuristicLlmAdapter.recommend(command);
        }
    }

    private GeminiClient.RecommendInput toLlmInput(DraftRecommendCommand command) {
        List<GeminiClient.ProjectItem> projects = (command.projects() == null) ? List.of() :
                command.projects().stream()
                        .map(p -> new GeminiClient.ProjectItem(p.projectId(), p.name()))
                        .collect(Collectors.toList());

        GeminiClient.RecentContext recent = null;
        if (command.recentContext() != null) {
            recent = new GeminiClient.RecentContext(
                    command.recentContext().projectId(),
                    command.recentContext().stage()
            );
        }

        return new GeminiClient.RecommendInput(projects, recent, command.fixedStages());
    }

    private Long normalizeProjectId(DraftRecommendCommand command, Long llmProjectId) {
        if (llmProjectId == null) return null;
        if (command.projects() == null || command.projects().isEmpty()) return null;

        boolean exists = command.projects().stream()
                .anyMatch(p -> Objects.equals(p.projectId(), llmProjectId));
        return exists ? llmProjectId : null;
    }

    private String normalizeStage(DraftRecommendCommand command, String llmStage) {
        if (llmStage != null && command.fixedStages().contains(llmStage)) {
            return llmStage;
        }
        if (command.recentContext() != null && DraftStage.isValid(command.recentContext().stage())) {
            return command.recentContext().stage();
        }
        return command.fixedStages().get(0);
    }

    private String normalizeSubtitle(String subtitle) {
        if (subtitle == null) return null;
        String t = subtitle.trim();
        return t.isEmpty() ? null : t;
    }
}
