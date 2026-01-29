package com.moa.moa_backend.domain.digest.llm.impl;

import com.moa.moa_backend.domain.digest.entity.DigestKind;
import com.moa.moa_backend.domain.digest.llm.ProjectDigestGeneratorPort;
import com.moa.moa_backend.domain.digest.service.DigestInputNormalizer;
import com.moa.moa_backend.domain.digest.service.DigestTextValidator;
import com.moa.moa_backend.domain.scrap.repository.projection.ScrapForDigestView;
import com.moa.moa_backend.global.llm.gemini.GeminiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * project digest 도메인 전용 Gemini 어댑터
 * - 프롬프트 조립 + Gemini 호출 + 마크다운 정리/검증
 *
 * (스크랩 → 의미 있는 LLM 입력으로 정제 → 프로젝트 전체 요약 프롬프트 생성 → Gemini 호출 → 마크다운만 정제/검증 → 반환)
 */
@Primary
@Component
public class GeminiProjectDigestAdapter implements ProjectDigestGeneratorPort {

    @Value("${moa.llm.digest-timeout-ms:15000}")
    private long digestTimeoutMs;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(KST);

    private final GeminiClient geminiClient;
    private final DigestTextValidator validator;

    public GeminiProjectDigestAdapter(GeminiClient geminiClient, DigestTextValidator validator) {
        this.geminiClient = geminiClient;
        this.validator = validator;
    }

    @Override
    public String generateMarkdown(
            String projectName,
            DigestKind kind,
            String customPrompt,
            List<ScrapForDigestView> scraps
    ) {
        String prompt = buildPrompt(projectName, kind, customPrompt, scraps);

        String raw = geminiClient.generateText(
                prompt,
                Duration.ofMillis(digestTimeoutMs)
        );

        String markdown = validator.normalize(raw);
        validator.validate(markdown);
        return markdown;
    }

    private String buildPrompt(
            String projectName,
            DigestKind kind,
            String customPrompt,
            List<ScrapForDigestView> scraps
    ) {
        String pn = safe(projectName);

        List<ScrapForDigestView> ordered = (scraps == null) ? List.of()
                : scraps.stream()
                .sorted(Comparator.comparing(ScrapForDigestView::capturedAt)
                        .thenComparing(ScrapForDigestView::scrapId))
                .toList();

        StringBuilder sb = new StringBuilder();

        if (kind == DigestKind.CUSTOM) {
            sb.append("""
            너는 사용자의 프로젝트 스크랩을 바탕으로 "%s" 프로젝트를 '사용자 요구사항'에 맞게 요약하는 기록 편집자다.

            반드시 "마크다운 본문만" 출력하라.
            (JSON/설명/코드블록/추가 텍스트/머리말 금지)

            ### 사용자 요구사항
            %s

            ### 강제 규칙(중요)
            - 사용자 요구사항을 최우선으로 따른다.
            - 애매한 내용은 단정하지 말고 "추정"으로 표시하라.
            - 코드블록(```), JSON, "아래는" 같은 설명 문구를 쓰지 마라.
            - 입력 스크랩을 그대로 길게 복사하지 마라(요약만).

            ### 출력 형식(권장)
            ## 한줄 요약
            - (1~2줄)

            ## 핵심 내용
            - (3~8개 불릿)

            ## 타임라인(선택)
            - 필요하면 시간순으로 "시간 - 작업 - 결과"

            ## 결정/변경(선택)
            - **결정:** ...
              - 근거: ...
              - 영향: ...
            - **변경:** A → B
              - 이유: ...
              - 근거: ...

            입력 스크랩(시간순):
            """.formatted(pn, safe(customPrompt)));
        } else {
            sb.append("""
            너는 사용자의 프로젝트 스크랩을 바탕으로 "%s" 프로젝트의 "전체 맥락 + 작업 흐름 + 의사결정/변경"을 요약하는 기록 편집자다.

            반드시 "마크다운 본문만" 출력하라.
            (JSON/설명/코드블록/추가 텍스트/머리말 금지)

            ### 최우선 목표
            1) 프로젝트에서 무엇을 어떤 순서로 진행했는지(작업 흐름)
            2) 어떤 결정들이 있었는지(결정/근거/영향 범위)
            3) 어떤 변경/되돌림이 있었는지(처음 A → 나중 B로 바뀜, 왜 바뀜)
            4) 서로 충돌하는 시도/대안이 있었으면 드러내기

            ### 결정/변경 트리거(참고)
            - 결정: "하기로 했다", "채택", "확정", "정했다", "통일", "정책", "허용/금지"
            - 변경: "바꿨다", "수정", "변경", "원복", "rollback", "폐기", "취소", "처음엔~였는데"

            ### 출력 형식(섹션 제목/순서 고정)
            ## 한줄 요약
            - (1~2줄)

            ## 핵심 내용
            - (3~8개 불릿)

            ## 작업 흐름 타임라인
            - 각 항목은 "시간 - 작업 - 결과" 형태
            - 개수 제한 없음

            ## 결정 사항
            - **(결정 문장)**
              - 근거: ...
              - 영향: ...
              - 관련 스크랩: (가능하면 capturedAt 또는 subtitle)

            ## 변경/되돌림
            - 바뀐 케이스가 없으면 이 섹션 자체를 생략
            - **변경:** A → B
              - 이유: ...
              - 근거: ...

            ### 금지
            - 코드블록(```), JSON, "아래는" 같은 설명 문구

            입력 스크랩(시간순):
            """.formatted(pn));
        }

        int included = 0;

        for (ScrapForDigestView s : ordered) {
            String stage = safe(extractStage(s));
            String subtitle = safe(s.subtitle());
            String memo = safe(s.memo());

            // rawHtml -> text 정규화 (투입 안전성 확보)
            String baseText = DigestInputNormalizer.normalizeRawHtml(s.rawHtml());

            // 완전 빈 입력 제거
            if (baseText.isBlank() && subtitle.isBlank() && memo.isBlank() && stage.isBlank()) {
                continue;
            }

            StringBuilder merged = new StringBuilder();

            // 프로젝트 요약은 stage가 있으면 컨텍스트에 도움이 되므로 포함(없으면 자동 생략)
            if (!stage.isBlank()) merged.append("[stage] ").append(stage).append(" ");
            if (!subtitle.isBlank()) merged.append("[subtitle] ").append(subtitle).append(" ");
            if (!memo.isBlank()) merged.append("[memo] ").append(memo).append(" ");

            if (!baseText.isBlank()) {
                // text-only clamp(800)
                String textOnly = DigestInputNormalizer.clampTextOnly(baseText);
                merged.append("[text] ").append(textOnly);
            }

            // merged clamp(1000)
            String finalText = DigestInputNormalizer.clampMergedPerScrap(merged.toString());

            sb.append("\n---\n");
            sb.append("capturedAt: ").append(TS.format(s.capturedAt())).append("\n");
            sb.append(finalText).append("\n");

            included++;
        }

        if (included == 0) {
            throw new IllegalArgumentException("project digest input scraps are empty after normalization");
        }

        // 전체 프롬프트 총량 제한
        return DigestInputNormalizer.clampTotal(sb.toString());
    }


    private String extractStage(ScrapForDigestView s) {
        return s.stage();
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}

