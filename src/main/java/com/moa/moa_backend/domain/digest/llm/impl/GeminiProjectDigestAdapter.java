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
            너는 사용자의 프로젝트 스크랩을 바탕으로 "%s" 프로젝트를
            '사용자 요구사항'에 맞게 요약하는 기록 편집자다.
                    
            반드시 "마크다운 본문만" 출력하라.
            (JSON/설명/코드블록/추가 텍스트/머리말 금지)
                    
            ### 사용자 요구사항
            %s
                    
            ### 기본 원칙(강제)
            - 사용자 요구사항을 최우선으로 따른다.
            - 다만, 아래 3가지는 어떤 요구사항에서도 유지한다:
                1) 사실성: 스크랩에 없는 내용은 단정하지 말고 "추정"으로 표시
                2) 결정/변경: 중요한 결정/변경이 있다면 반드시 포함
                3) 근거: 주장에는 가능하면 관련 스크랩 힌트(stage/capturedAt/subtitle)를 1개 이상 붙임
                - 입력 스크랩을 그대로 길게 복사하지 말고 요약만 하라.
                
            ### 조건부 구성 규칙(강제)
            - 사용자 요구사항에 아래 키워드가 포함되면:
                ("면접", "자기소개", "소개", "포트폴리오", "이력서", "채용", "성과", "구직", "취업", "기여")
                출력에서 "핵심 성과/기여/임팩트" 섹션을 가장 비중있게 작성하라.
                - 성과는 가능한 "무엇을 -> 어떻게 -> 결과 임팩트" 형태로 써라
                - 성과는 정량적/통계적 지표가 있으면 더 좋지만 없다면 억지로 쓰지 마라.
                - 수치가 없으면 억지로 만들지 말고 "정성적 효과"로 표현하되, 근거 스크랩을 반드시 연결할 것.
                
                    
            ### stage 사용 규칙(권장)
            - stage는 섹션 분리용이 아니라 근거 라벨이다.
            - 필요할 때만 (stage)로 붙여라.
                    
            ### 출력 형식(권장, 요구사항이 다른 형식을 요구하면 조정 가능)
                    
            ## 핵심 성과/가치(요구사항에 맞춤)
            - (3~8개)
                    
            ## 결정/변경(있다면)
            - **결정/변경:** ...
                - 근거: ...
                - 관련 스크랩: (stage) ...
                    
            ## 타임라인(필요하면)
            - 시간 - 작업 - 결과 (stage)
                          
            ### 금지
            - 코드블록(```), JSON, "아래는" 같은 설명 문구

            입력 스크랩(시간순):
            """.formatted(safe(customPrompt), pn));
        } else {
            sb.append("""
            너는 사용자의 프로젝트 스크랩을 바탕으로 "%s" 프로젝트의 "전체 맥락 + 작업 흐름 + 의사결정/변경"을 요약하는 기록 편집자다.

            반드시 "마크다운 본문만" 출력하라.
            (JSON/설명/코드블록/추가 텍스트/머리말 금지)

            ### 프로젝트 요약의 목표(중요)
            - 단순히 스크랩의 내용을 나열하지 말고, "프로젝트가 진행된 흐름"을 복원하라.
            - 서로 다른 작업단계(stage)간 연결을 보아라.
                예: 기획에서 목표/제약 -> 설계 결정 -> 구현 선택 -> 테스트/운영 이슈로 이어짐
            - 사용자의 의사결정 과정을 드러내라 :
                "왜 이선택을 했는지", "대안은 무엇이었는지", "무엇이 바뀌었는지", "그 영향은 무엇인지"
              
            ### stage 사용 규칙 (강제)
            - stage는 섹션을 쪼개는 용도가 아니라 "근거 라벨"로서 사용하라.
            - 타임라인/결정/변경 항목에 가능하면 (stage)를 붙여라.
            
            ### 사실성 규칙(강제) 
            - 스크랩에 없는 내용을 단정하지 말라.
            - 불확실하면 "추정" 또는 "가능성"으로 표시하라.
            - 입력을 길게 복사하지 말고, 요약/재구성만 하라.
            
            ### 결정/변경을 찾는 힌트(참고)
            - 결정: "채택/확정/정했다/통일/정책/허용/금지/선택"
            - 변경: "바꿈/수정/변경/원복/rollback/폐기/취소/처음엔~였는데"
            - 트러블슈팅: "에러/실패/원인/해결/우회/재현/대응"

            ### 출력 형식(섹션 제목/순서 고정)

            ## 프로젝트 맥락(왜 이걸 했는가)
            - 문제/목표/제약(시간/기술/팀/환경)이 무엇이었는지 3~6개 불릿
            
            ## 핵심 흐름 (단계 연결)
            - "기획 -> 조사/분석 -> 설계 -> 구현 -> 테스트/운영 -> 발표 / 기타" 흐름이 끊김없이 7~10개 불릿으로 재구성
            - 각 불릿은 "다른 단계의 결과/제약"이 다른 단계 선택에 어떻게 영향을 줬는지 가능하면 포함하라.

            ## 작업 흐름 타임라인
            - 각 항목은 "시간 - 작업 - 결과 (stage)" 형태
            - 시간이 불명확하면 "대략"으로 표기해도 된다
            - 중요한 작업 흐름 위주로 타임라인을 표기하라 (결정/산출물/이슈 해결/정책 변경 포함)
            - 개수 제한 없으나 같은 내용 반복은 합치되, 결론/정책/설계가 바뀌는 '변경'은 절대 합치지 마라.

            ## 결정 사항 (가장 중요)
            - 3~8개를 선별해서 작성(가치 있는 결정만)
            - 각 항목 형식:
                - **결정:** (한 문장)
                - 근거: (왜 그렇게 했나)
                - 대안/트레이드오프: (가능하면 1개 이상)
                - 영향: (무엇이 좋아졌거나 나빠졌나)
                - 관련 스크랩: (stage) subtitle 1~2개

            ## 변경/되돌림
            - 바뀐 케이스가 없으면 이 섹션 자체를 생략
            - **변경:** A → B
              - 이유: ...
              - 근거: ...
              - 관련 스크랩: (stage) subtitle 1~2개

            ### 미해결/리스크
            - 없다면 이 섹션 자체를 생략
            - 남아있는 문제/이슈/리스크가 무엇인지 간략히 3~5개 불릿
            
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
            if (!stage.isBlank()) merged.append("[stage] ").append(stage).append("\n");
            if (!subtitle.isBlank()) merged.append("[subtitle] ").append(subtitle).append("\n");
            if (!memo.isBlank()) merged.append("[memo] ").append(memo).append("\n");

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

