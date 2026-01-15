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

    private final GeminiClient geminiClient;    //http 통신
    private final HeuristicLlmAdapter heuristicLlmAdapter;  //fallback

    public GeminiLlmAdapter(GeminiClient geminiClient, HeuristicLlmAdapter heuristicLlmAdapter) {
        this.geminiClient = geminiClient;
        this.heuristicLlmAdapter = heuristicLlmAdapter;
    }

    @Override
    public DraftRecommendation recommend(DraftRecommendCommand command) {

        // 1. 사전검증 : fixedStages가 있어야만 추천 가능 없으면 불가능
        if (command.fixedStages() == null || command.fixedStages().isEmpty()) {
            throw new IllegalStateException("fixedStages must not be empty");
        }

        try {
            //2. llm입력 변환
            GeminiClient.RecommendInput input = toLlmInput(command);

            //3. gemini 호출
            GeminiClient.LlmResult llm = geminiClient.recommend(input);

            // 4. 결과 정규화
            Long projectId = normalizeProjectId(command, llm.projectId()); // 프로젝트 없으면 null 가능(케이스2)
            String stage = normalizeStage(command, llm.stage());           // 항상 fixedStages 내로
            String subtitle = normalizeSubtitle(llm.subtitle());           // LLM 성공이면 허용

            //5. draftRecommendation 생성
            return new DraftRecommendation(
                    projectId,
                    stage,
                    subtitle,
                    RecMethod.LLM
            );

        } catch (GeminiClientException e) {
            // LLM 실패: 폴백 규칙 적용 ----
            return heuristicLlmAdapter.recommend(command);
        }
    }

    //-------데이터 변환--------
    private GeminiClient.RecommendInput toLlmInput(DraftRecommendCommand command) {
        List<GeminiClient.ProjectItem> projects = (command.projects() == null)
                ? List.of() //null이면 빈 리스트
                : command.projects().stream()
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

    //-----프로젝트 ID 검증---------
    private Long normalizeProjectId(DraftRecommendCommand command, Long llmProjectId) {
        //llm이 null -> null 반환
        if (llmProjectId == null) return null;

        // 사용자가 프로젝트가 없다면 null 반환
        if (command.projects() == null || command.projects().isEmpty()) return null;

        //llm이 추천한 projectId가 실제로 사용자 프로젝트 목록에 있는지 검증
        boolean exists = command.projects().stream()
                .anyMatch(p -> Objects.equals(p.projectId(), llmProjectId));
        return exists ? llmProjectId : null;
    }

    // -------작업단계 검증--------
    private String normalizeStage(DraftRecommendCommand command, String llmStage) {
        //llm 이 준 작업단계가 유효하면 사용
        if (llmStage != null && command.fixedStages().contains(llmStage)) {
            return llmStage;
        }
        //최근 스트랩의 작업단계 사용
        if (command.recentContext() != null && DraftStage.isValid(command.recentContext().stage())) {
            return command.recentContext().stage();
        }
        // 기본값은 '기획'으로 설정
        return command.fixedStages().get(0);
    }

    // -----소제목 정리----
    private String normalizeSubtitle(String subtitle) {
        if (subtitle == null) return null;
        String t = subtitle.trim();
        return t.isEmpty() ? null : t;
    }
}
