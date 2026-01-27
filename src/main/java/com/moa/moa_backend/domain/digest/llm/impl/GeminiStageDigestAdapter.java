package com.moa.moa_backend.domain.digest.llm.impl;

import com.moa.moa_backend.domain.digest.llm.StageDigestGeneratorPort;
import com.moa.moa_backend.domain.digest.service.DigestInputNormalizer;
import com.moa.moa_backend.domain.digest.service.DigestTextValidator;
import com.moa.moa_backend.domain.scrap.repository.ScrapForDigestView;
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
 * digest 도메인 전용 Gemini 어댑터
 * - 프롬프트 조립 + Gemini 호출 + 마크다운 정리/검증
 * (스크랩 → 의미 있는 LLM 입력으로 정제 → 결정/변경 중심 프롬프트 생성 → Gemini 호출 → 마크다운만 정제/검증 → 반환)
 */
@Primary
@Component
public class GeminiStageDigestAdapter implements StageDigestGeneratorPort {

    @Value("${moa.llm.digest-timeout-ms:15000}")
    private long digestTimeoutMs;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(KST);

    private final GeminiClient geminiClient;
    private final DigestTextValidator validator;

    public GeminiStageDigestAdapter(GeminiClient geminiClient, DigestTextValidator validator) {
        this.geminiClient = geminiClient;
        this.validator = validator;
    }

    @Override
    public String generateMarkdown(String projectName, String stage, List<ScrapForDigestView> scraps) {
        String prompt = buildPrompt(projectName, stage, scraps);

        String raw = geminiClient.generateText(
                prompt,
                Duration.ofMillis(digestTimeoutMs)
        );

        String markdown = validator.normalize(raw);
        validator.validate(markdown);
        return markdown;
    }


    private String buildPrompt(String projectName, String stage, List<ScrapForDigestView> scraps) {
        List<ScrapForDigestView> ordered = scraps == null ? List.of() :
                //스크랩 시간순으로 정렬
                scraps.stream().sorted(Comparator.comparing(ScrapForDigestView::capturedAt)).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("""
        너는 사용자의 작업 스크랩을 바탕으로 "%s" 프로젝트의 "%s" 단계에서 발생한
        "작업 흐름 + 의사결정 흐름 + 변경/되돌림 흐름"을 요약하는 기록 편집자다.
    
        반드시 "마크다운 본문만" 출력하라.
        (JSON/설명/코드블록/추가 텍스트/머리말 금지)
    
        ### 최우선 목표
        1) 사용자가 이 단계에서 무엇을 어떤 순서로 진행했는지(작업 흐름)
        2) 어떤 결정들이 있었는지(결정/근거/영향 범위)
        3) 어떤 변경/되돌림이 있었는지(처음 A → 나중 B로 바뀜, 왜 바뀜)
        4) 서로 충돌하는 시도/대안이 있었으면 모두 드러내기
    
        ### 결정/변경 "트리거" (이 표현이 나오면 결정/변경으로 간주해 반드시 뽑아라)
        - 결정 트리거 예:
          "하기로 했다", "채택한다", "선택했다", "결론", "확정", "정했다", "통일", "표준화",
          "API는 ~로 간다", "스키마는 ~로 한다", "정책", "허용", "금지"
        - 변경/되돌림 트리거 예:
          "바꿨다", "수정", "변경", "되돌렸다", "rollback", "폐기", "취소", "다시", "원복",
          "처음엔 ~였는데", "문제라서", "에러가 나서", "이슈로", "불가능해서", "성능 때문에"
    
        ### 강제 규칙(중요)
        - "결정 사항"은 개수를 제한하지 말고, 스크랩에 등장하는 결정은 최대한 빠짐없이 뽑아라.
        - "작업 흐름 타임라인"도 개수를 제한하지 말고, 의미 있는 이벤트는 전부 시간순으로 나열하라.
          (단, 같은 내용이 반복될 경우 하나로 합치되, 결론/정책/설계가 바뀌는 '변경'은 절대 합치지 마라)
        - "다음 액션" 섹션은 만들지 마라.
        - 스크랩의 memo/subtitle/text에서 "근거"를 찾아 결정과 연결해라.
        - 애매한 내용은 단정하지 말고 "추정"으로 표시하라.
        - 가능한 경우 "무엇(대상)" + "결정(행위)" + "근거(이유)" + "영향(범위)"를 모두 포함해라.
    
        ### 출력 형식(섹션 제목/순서 고정)
        ## 한줄 요약
        - (1~2줄로 전체 변화/핵심 결정을 요약)
    
        ## 작업 흐름 타임라인
        - 각 항목은 "시간 - 행동/작업 - 결과" 형태
        - 예: 2026-01-10 - API 설계 초안 작성 - GET /scraps cursor 방식 검토
        - 결과는 가능하면 "결정/산출물/이슈 해결/정책 변경"까지 포함해라.
        - 타임라인 개수 제한 없음
    
        ## 결정 사항(최대한 많이)
        - 각 항목은 아래 포맷을 반드시 따를 것 (개수 제한 없음)
          - **결정:** (무엇을 하기로 했는지)
            - 근거: (스크랩에서 확인되는 근거/메모/토론 포인트)
            - 영향: (변경되는 범위: DB/API/코드/UX/운영 등)
            - 관련 스크랩: (가능하면 capturedAt 또는 subtitle 1줄로)
    
        ## 변경/되돌림(있다면)
        - 바뀐 케이스를 전부 나열
        - 각 항목 포맷:
          - **변경:** A → B
            - 이유: (왜 바뀌었는지)
            - 근거: (스크랩 텍스트/메모 기반)
    
        ### 금지
        - 코드블록(```), JSON, "아래는" 같은 설명 문구
        - 입력 스크랩을 그대로 길게 복사 (요약만)
        - 다음 액션/할 일 제안
    
        입력 스크랩(시간순):
        """.formatted(projectName, stage));

        int included = 0;

        for (ScrapForDigestView s : ordered) {
            // service에서 이미 정규화된 텍스트를 rawHtml 필드에 넣어서 넘긴다는 전제
            String baseText = (s.rawHtml() == null) ? "" : s.rawHtml();
            if (baseText.isBlank()
                    && (s.subtitle() == null || s.subtitle().isBlank())
                    && (s.memo() == null || s.memo().isBlank())) {
                continue;
            }

            StringBuilder merged = new StringBuilder();

            if (s.subtitle() != null && !s.subtitle().isBlank()) {
                merged.append("[subtitle] ").append(s.subtitle()).append(" ");
            }
            if (s.memo() != null && !s.memo().isBlank()) {
                merged.append("[memo] ").append(s.memo()).append(" ");
            }
            // text는 "text-only clamp(800)"를 한 번 더 적용해서 비중/길이 제어
            // - 서비스에서 이미 normalizeRawHtml로 1차 정규화/클램프 되었더라도,
            //   merged 정책에 맞춰 여기서 최종적으로 다시 제한해도 안전함.
            if (!baseText.isBlank()) {
                String textOnly = DigestInputNormalizer.clampTextOnly(baseText);
                merged.append("[text] ").append(textOnly);
            }

            // merged(subtitle+memo+text)는 "merged clamp(1000)" 기준으로 제한
            // - 기존 clampPerScrap(=800)는 text 기준이라 merged에 쓰면 너무 잘림
            String finalText = DigestInputNormalizer.clampMergedPerScrap(merged.toString());

            sb.append("\n---\n");
            sb.append("capturedAt: ").append(TS.format(s.capturedAt())).append("\n");
            sb.append(finalText).append("\n");

            included++;
        }

        if (included == 0) {
            throw new IllegalArgumentException("digest input scraps are empty after normalization");
        }


        return DigestInputNormalizer.clampTotal(sb.toString());
    }

}
