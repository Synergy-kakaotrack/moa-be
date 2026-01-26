package com.moa.moa_backend.domain.digest.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DigestTextValidator {

    public void validate(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            throw new IllegalArgumentException("digest markdown is required");
        }
    }

    public String normalize(String raw) {
        if (raw == null) return null;
        String t = raw.trim();

        // 혹시 LLM이 ```markdown ...```로 감싸면 제거
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\s*", "");
            t = t.replaceFirst("\\s*```\\s*$", "");
        }
        return t.trim();
    }
}
