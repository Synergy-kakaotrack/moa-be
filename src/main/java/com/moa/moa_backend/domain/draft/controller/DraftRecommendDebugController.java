package com.moa.moa_backend.domain.draft.controller;

import com.moa.moa_backend.domain.draft.dto.DraftRecommendCommand;
import com.moa.moa_backend.domain.draft.dto.DraftRecommendation;
import com.moa.moa_backend.domain.draft.llm.LlmRecommendationPort;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/debug/recommend")
public class DraftRecommendDebugController {

    private final LlmRecommendationPort llm;

    public DraftRecommendDebugController(LlmRecommendationPort llm) {
        this.llm = llm;
    }

    /**
     * 실제 Gemini 호출 + Adapter 정책(LLM / FALLBACK / NONE)까지 확인용
     *
     * GET /debug/recommend/sample
     */
    @GetMapping("/sample")
    public DraftRecommendation sample() {

        //프로젝트 옵션 (ProjectOption)
        List<DraftRecommendCommand.ProjectOption> projects = List.of(
                new DraftRecommendCommand.ProjectOption(1L, "MOA"),
                new DraftRecommendCommand.ProjectOption(2L, "캡스톤")
        );

        // 최근 컨텍스트 (RecentContext)
        DraftRecommendCommand.RecentContext recentContext =
                new DraftRecommendCommand.RecentContext(
                        2L,
                        "DESIGN",
                        Instant.now()
                );

        //고정 단계
        List<String> fixedStages = List.of(
                "RESEARCH",
                "DESIGN",
                "IMPLEMENT",
                "TEST"
        );

        // DraftRecommendCommand 생성
        DraftRecommendCommand command = new DraftRecommendCommand(
                1L,                                 // userId
                "이 문서는 로그인 기능 설계에 대한 메모입니다.", // contentPlain
                "chrome-extension",                 // sourceCode
                "https://example.com",               // sourceUrl
                projects,
                fixedStages,
                recentContext,
                Instant.now()
        );

        // 핵심: 여기서 GeminiLlmAdapter → GeminiClient 실제 호출
        return llm.recommend(command);
    }
}
