package com.moa.moa_backend.global.llm.gemini;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class GeminiClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public GeminiClient(
            WebClient.Builder builder,
            @Value("${moa.llm.gemini.api-key}") String apiKey,
            @Value("${moa.llm.gemini.model:gemini-2.0-flash}") String model,
            @Value("${moa.llm.timeout-ms:3000}") long timeoutMs
    ) {
        this.webClient = builder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    public String generateText(String prompt) {
        try {
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

            if (response == null) throw new GeminiClientException("Gemini response is null");
            return extractText(response);

        } catch (WebClientResponseException e) {
            throw new GeminiClientException("Gemini HTTP error: " + e.getStatusCode(), e);
        } catch (GeminiClientException e) {
            throw e;
        } catch (Exception e) {
            throw new GeminiClientException("Gemini call failed", e);
        }
    }

    private Map<String, Object> requestBody(String prompt) {
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
}
