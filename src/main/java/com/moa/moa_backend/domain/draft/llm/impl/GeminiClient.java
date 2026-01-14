package com.moa.moa_backend.domain.draft.llm.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Gemini API 호출/타임아웃/JSON 파싱만 담당.
 * 비즈니스 정책(RecMethod 결정 등)은 여기서 하지 않는다.
 *
 * - 성공: LlmResult(projectId, stage, subtitle) 반환
 * - 실패(HTTP/timeout/빈 응답/파싱 실패): GeminiClientException throw
 */
@Component
public class GeminiClient {

    private final WebClient webClient;
    private final ObjectMapper om;

    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public GeminiClient(
            WebClient.Builder builder,
            @Value("${moa.llm.gemini.api-key}") String apiKey,
            @Value("${moa.llm.gemini.model:gemini-2.0-flash}") String model,
            @Value("${moa.llm.timeout-ms:3000}") long timeoutMs
    ) {
        this.webClient = builder
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();

        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofMillis(timeoutMs);

        this.om = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    //Adapter가 호출하는 메서드: 입력 -> (Gemini 호출) -> JSON 파싱 결과
    public LlmResult recommend(RecommendInput input) {
        try {
            String prompt = buildPrompt(input);

            GeminiResponse response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", apiKey)
                            .build(model)
                    )
                    .bodyValue(requestBody(prompt))
                    .retrieve()
                    .bodyToMono(GeminiResponse.class)
                    .block(timeout);

            if (response == null) {
                throw new GeminiClientException("Gemini response is null");
            }

            String text = extractText(response); // Gemini가 준 텍스트 (우리는 JSON 문자열을 기대)
            return parseLlmResult(text);

        } catch (WebClientResponseException e) {
            // 4xx/5xx 포함
            throw new GeminiClientException("Gemini HTTP error: " + e.getStatusCode(), e);
        } catch (GeminiClientException e) {
            // 이미 래핑된 예외는 그대로
            throw e;
        } catch (Exception e) {
            // 타임아웃/파싱/런타임 등
            throw new GeminiClientException("Gemini call failed", e);
        }
    }

    private Map<String, Object> requestBody(String prompt) {
        // 최소 스펙. (추후 generationConfig/temperature 등 확장 가능)
        return Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );
    }

    private String extractText(GeminiResponse response) {
        if (response.candidates() == null || response.candidates().isEmpty()) {
            throw new GeminiClientException("Gemini candidates empty");
        }
        var content = response.candidates().get(0).content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            throw new GeminiClientException("Gemini content/parts empty");
        }
        String text = content.parts().get(0).text();
        if (text == null || text.isBlank()) {
            throw new GeminiClientException("Gemini text empty");
        }
        return text;
    }

    private LlmResult parseLlmResult(String rawText) {
        // Gemini가 JSON만 주도록 프롬프트에 강하게 지시했지만,
        // 혹시 몰라서 앞뒤 잡텍스트를 제거하는 간단한 방어도 가능.
        // 여기서는 "첫 { 부터 마지막 }"로 한번 정리.
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

    private String buildPrompt(RecommendInput input) {
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

        // 핵심: JSON만 출력 + stage는 fixedStages 중 하나 + projectId는 목록에서만 (없으면 null)
        return """
                너는 브라우저 스크랩 저장을 돕는 추천 엔진이다.
                아래 입력을 참고해서 JSON만 출력하라. (설명/코드블록/추가 텍스트 금지)

                제약:
                - stage는 반드시 fixedStages 중 하나여야 한다.
                - projectId는 projects 목록에 있는 값만 선택할 수 있다.
                - 적절한 프로젝트가 없다면 projectId는 null로 둬라.
                - subtitle은 짧은 한 줄(선택). 없으면 null.

                입력:
                projects=%s
                recentContext=%s
                fixedStages=%s

                출력(JSON만):
                {"projectId": number|null, "stage": string, "subtitle": string|null}
                """.formatted(projectsStr, recentStr, fixedStagesStr);
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"");
    }

    // ----- LLM용 입력/출력 DTO -----

    public record RecommendInput(
            List<ProjectItem> projects,
            RecentContext recent,
            List<String> fixedStages
    ) {}

    public record ProjectItem(Long projectId, String title) {}

    public record RecentContext(Long projectId, String stage) {}

    public record LlmResult(Long projectId, String stage, String subtitle) {}
}


