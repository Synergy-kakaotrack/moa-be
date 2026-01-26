package com.moa.moa_backend.domain.draft.llm.impl;

import com.moa.moa_backend.domain.draft.dto.DraftRecommendCommand;
import com.moa.moa_backend.domain.draft.dto.DraftRecommendation;
import com.moa.moa_backend.domain.draft.entity.DraftStage;
import com.moa.moa_backend.domain.draft.entity.RecMethod;
import com.moa.moa_backend.domain.draft.llm.LlmRecommendationPort;
import com.moa.moa_backend.global.error.ApiException;
import com.moa.moa_backend.global.error.ErrorCode;
import com.moa.moa_backend.global.llm.gemini.GeminiClient;
import com.moa.moa_backend.global.llm.gemini.GeminiClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Primary
@Component
public class GeminiLlmAdapter implements LlmRecommendationPort {

    private final GeminiClient geminiClient;          // ✅ global 엔진
    private final GeminiDraftCodec codec;             // ✅ prompt/parse 담당
    private final HeuristicLlmAdapter heuristicLlmAdapter;

    public GeminiLlmAdapter(GeminiClient geminiClient, GeminiDraftCodec codec, HeuristicLlmAdapter heuristicLlmAdapter) {
        this.geminiClient = geminiClient;
        this.codec = codec;
        this.heuristicLlmAdapter = heuristicLlmAdapter;
    }

    @Override
    public DraftRecommendation recommend(DraftRecommendCommand command) {
        if (command.fixedStages() == null || command.fixedStages().isEmpty()) {
            throw new ApiException(ErrorCode.DRAFT_RECOMMENDATION_CONFIG_INVALID);
        }

        try {
            GeminiDraftCodec.RecommendInput input = toLlmInput(command);

            String prompt = codec.buildPrompt(input);
            String rawText = geminiClient.generateText(prompt);      // ✅ 공통 호출
            GeminiDraftCodec.LlmResult llm = codec.parseResult(rawText);

            Long projectId = normalizeProjectId(command, llm.projectId());
            String stage = normalizeStage(command, llm.stage());
            String subtitle = normalizeSubtitle(llm.subtitle());

            return new DraftRecommendation(projectId, stage, subtitle, RecMethod.LLM);

        } catch (GeminiClientException e) {
            log.warn("[LLM] failed -> fallback. reason={}", e.getMessage(), e);
            return heuristicLlmAdapter.recommend(command);
        }
    }

    private GeminiDraftCodec.RecommendInput toLlmInput(DraftRecommendCommand command) {
        List<GeminiDraftCodec.ProjectItem> projects = (command.projects() == null)
                ? List.of()
                : command.projects().stream()
                .map(p -> new GeminiDraftCodec.ProjectItem(p.projectId(), p.name()))
                .collect(Collectors.toList());

        GeminiDraftCodec.RecentContext recent = null;
        if (command.recentContext() != null) {
            recent = new GeminiDraftCodec.RecentContext(
                    command.recentContext().projectId(),
                    command.recentContext().stage()
            );
        }

        return new GeminiDraftCodec.RecommendInput(
                command.contentPlain(),
                command.aiSource(),
                command.aiSourceUrl(),
                projects,
                recent,
                command.fixedStages()
        );
    }

    // 이하 normalize* 는 너 기존 코드 그대로
    private Long normalizeProjectId(DraftRecommendCommand command, Long llmProjectId) {
        boolean hasProjects = command.projects() != null && !command.projects().isEmpty();
        if (!hasProjects) return null;

        List<Long> ids = command.projects().stream()
                .map(p -> p.projectId())
                .filter(Objects::nonNull)
                .toList();

        if (llmProjectId != null && ids.contains(llmProjectId)) return llmProjectId;

        if (command.recentContext() != null) {
            Long recentId = command.recentContext().projectId();
            if (recentId != null && ids.contains(recentId)) return recentId;
        }

        if (ids.size() == 1) return ids.get(0);

        throw new ApiException(ErrorCode.DRAFT_RECOMMENDATION_INVALID);
    }

    private String normalizeStage(DraftRecommendCommand command, String llmStage) {
        if (llmStage != null) {
            String s = llmStage.trim();
            if (!s.isEmpty() && command.fixedStages().contains(s)) return s;
        }
        if (command.recentContext() != null) {
            String recentStage = command.recentContext().stage();
            if (recentStage != null) {
                recentStage = recentStage.trim();
                if (DraftStage.isValid(recentStage)) return recentStage;
            }
        }
        return command.fixedStages().get(0);
    }

    private String normalizeSubtitle(String subtitle) {
        if (subtitle == null) return null;
        String t = subtitle.trim();
        return t.isEmpty() ? null : t;
    }
}
