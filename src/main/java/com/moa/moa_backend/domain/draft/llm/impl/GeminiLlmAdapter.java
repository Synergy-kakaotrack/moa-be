package com.moa.moa_backend.domain.draft.llm.impl;

import com.moa.moa_backend.domain.draft.dto.DraftRecommendCommand;
import com.moa.moa_backend.domain.draft.dto.DraftRecommendation;
import com.moa.moa_backend.domain.draft.entity.DraftStage;
import com.moa.moa_backend.domain.draft.entity.RecMethod;
import com.moa.moa_backend.domain.draft.llm.LlmRecommendationPort;
import com.moa.moa_backend.global.error.ApiException;
import com.moa.moa_backend.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Gemini 결과를 정책대로 정규화하고, 실패 시 휴리스틱으로 폴백한다.
 */
@Slf4j
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
            throw new ApiException(ErrorCode.DRAFT_RECOMMENDATION_CONFIG_INVALID);
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
            log.warn("[LLM] failed -> fallback. reason={}", e.getMessage(), e);

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

        return new GeminiClient.RecommendInput(
                command.contentPlain(),
                command.aiSource(),
                command.aiSourceUrl(),
                projects,
                recent,
                command.fixedStages()
        );

    }

    //-----프로젝트 ID 검증---------
    private Long normalizeProjectId(DraftRecommendCommand command, Long llmProjectId) {

        boolean hasProjects = command.projects() != null && !command.projects().isEmpty();

        // 프로젝트가 아예 없으면 null 정상
        if (!hasProjects) return null;

        // 후보 ID 목록
        List<Long> ids = command.projects().stream()
                .map(p -> p.projectId())
                .filter(Objects::nonNull)
                .toList();

        // 1) LLM projectId가 유효하면 사용
        if (llmProjectId != null && ids.contains(llmProjectId)) return llmProjectId;

        // 2) recentContext로 보정
        if (command.recentContext() != null) {
            Long recentId = command.recentContext().projectId();
            if (recentId != null && ids.contains(recentId)) return recentId;
        }

        // 3) 프로젝트가 1개면 그걸로 강제 선택
        if (ids.size() == 1) return ids.get(0);

        // 4) 여기 오면 프로젝트가 있는데도 projectId를 못 정함 → 서버 정책/정합성 오류
        throw new ApiException(ErrorCode.DRAFT_RECOMMENDATION_INVALID);
    }



    // -------작업단계 검증--------
    private String normalizeStage(DraftRecommendCommand command, String llmStage) {

        // llm 이 준 작업단계가 유효하면 사용 (trim 적용)
        if (llmStage != null) {
            String s = llmStage.trim();
            if (!s.isEmpty() && command.fixedStages().contains(s)) {
                return s;
            }
        }
        //최근 스트랩의 작업단계 사용
        if (command.recentContext() != null) {
            String recentStage = command.recentContext().stage();
            if (recentStage != null) {
                recentStage = recentStage.trim();
                if (DraftStage.isValid(recentStage)) return recentStage;
            }
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
