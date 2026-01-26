package com.moa.moa_backend.domain.digest.service;

/**
 * LLM 입력에 넣기 전 raw_html을 텍스트로 정리하고, 최대 길이를 제한한다
 * MVP는 간단한 regex 기반
 */
public final class DigestInputNormalizer {

    private DigestInputNormalizer() {}

    // char 기준 제한 (토큰은 언어/내용에 따라 달라서 char로 1차 제한하는 게 단순)
    public static final int MAX_RAW_TEXT_LENGTH = 1500;

    public static String normalizeRawHtml(String rawHtml) {
        if (rawHtml == null) return null;

        String text = rawHtml
                .replaceAll("<script[^>]*>[\\s\\S]*?</script>", "")
                .replaceAll("<style[^>]*>[\\s\\S]*?</style>", "")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (text.length() > MAX_RAW_TEXT_LENGTH) {
            text = text.substring(0, MAX_RAW_TEXT_LENGTH) + "...";
        }
        return text;
    }
}
