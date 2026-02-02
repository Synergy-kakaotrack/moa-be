package com.moa.moa_backend.domain.draft.llm.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moa.moa_backend.global.llm.gemini.GeminiClientException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class GeminiDraftCodec {

    private final ObjectMapper om;

    public GeminiDraftCodec() {
        this.om = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public String buildPrompt(RecommendInput input) {
        String projectsStr = input.projects().stream()
                .map(p -> String.format("{\"projectId\":%d,\"title\":\"%s\"}", p.projectId(), escape(p.title())))
                .collect(Collectors.joining(",", "[", "]"));

        String recentStr = (input.recent() == null) ? "null"
                : String.format("{\"projectId\":%s,\"stage\":\"%s\"}",
                input.recent().projectId() == null ? "null" : input.recent().projectId().toString(),
                escape(input.recent().stage())
        );

        String fixedStagesStr = input.fixedStages().stream()
                .map(s -> "\"" + escape(s) + "\"")
                .collect(Collectors.joining(",", "[", "]"));

        String scrapText = limit(input.contentPlain(), 2000);
        String url = input.aiSourceUrl() == null ? "" : input.aiSourceUrl();
        String source = input.aiSource() == null ? "" : input.aiSource();

        return """
        너는 브라우저 스크랩 저장을 돕는 추천 엔진이다.
        아래 입력을 참고해서 JSON만 출력하라. (설명/코드블록/추가 텍스트 금지)
        
        목표:
        - scrapText의 주제/행동을 근거로 stage와 subtitle을 추천한다.
        
        제약:
        - stage는 반드시 fixedStages 중 하나여야 한다.
        - projects가 비어있으면 projectId는 null로 출력하라.
        - projects가 1개 이상이면 projectId는 반드시 projects 중 하나(number)여야 하며 null 금지.
        - projects가 1개 이상이고 recentContext.projectId가 null이 아니면, projectId는 반드시 recentContext.projectId로 출력하라 (scrapText가 매우 명확히 다른 프로젝트를 가리키는 경우만 예외).
        - subtitle은 scrapText를 15~25자 내로 요약해라. 가능하면 null을 쓰지 마라.
        - subtitle은 scrapText를 요약하되 "최대 25자"를 절대 초과하지 마라. (한글/영문/숫자/기호 섞여도 동일)
        - 25자를 넘길 것 같으면 더 짧게 다시 요약해서 25자 이하로 맞춰라.
        - 반드시 JSON 객체 1개만 출력하고, 앞뒤로 어떤 문자도 붙이지 마라.
        
        
        입력:
        projects=%s
        recentContext=%s
        fixedStages=%s
        aiSource=%s
        aiSourceUrl=%s
        scrapText=%s

        출력(JSON만):
        {"projectId": number|null, "stage": string, "subtitle": string|null}
        ※ 단, projects가 1개 이상이면 projectId는 반드시 number여야 한다(null 금지).
        """.formatted(
                projectsStr,
                recentStr,
                fixedStagesStr,
                jsonString(source),
                jsonString(url),
                jsonString(scrapText)
        );
    }

    public LlmResult parseResult(String rawText) {
        String json = stripToJsonObject(rawText);
        try {
            return om.readValue(json, LlmResult.class);
        } catch (Exception e) {
            throw new GeminiClientException("Gemini JSON parse failed: " + rawText, e);
        }
    }

    private String stripToJsonObject(String s) {
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end < 0 || end <= start) {
            throw new GeminiClientException("No JSON object found in Gemini output: " + s);
        }
        return s.substring(start, end + 1);
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"");
    }

    private String limit(String s, int max) {
        if (s == null) return "";
        s = s.strip();
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String jsonString(String s) {
        if (s == null) s = "";
        return "\"" + escape(s) + "\"";
    }

    // ----- LLM용 입력/출력 DTO -----

    public record RecommendInput(
            String contentPlain,
            String aiSource,
            String aiSourceUrl,
            List<ProjectItem> projects,
            RecentContext recent,
            List<String> fixedStages
    ) {}

    public record ProjectItem(Long projectId, String title) {}
    public record RecentContext(Long projectId, String stage) {}
    public record LlmResult(Long projectId, String stage, String subtitle) {}
}


