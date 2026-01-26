package com.moa.moa_backend.domain.digest.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public final class DigestInputNormalizer {

    public static final int MAX_TEXT_PER_SCRAP = 1500; // 스크랩 1개당 최대 글자
    public static final int MAX_TOTAL_TEXT = 20000;    // stage 전체 합산 최대 글자
    public static final int MIN_TEXT_LENGTH = 50;      // 이보다 짧으면 잡음으로 보고 제외

    private DigestInputNormalizer() {}

    /**
     * raw_html -> LLM 입력용 텍스트
     * - HTML 태그 제거 + 잡음 영역 제거 + 공백 정리 + 길이 제한
     */
    public static String normalizeRawHtml(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) return "";

        Document doc = Jsoup.parse(rawHtml);

        // 1) 명확한 잡음 제거
        doc.select("script, style, noscript").remove();

        // 2) 레이아웃/네비 영역 제거(사이트 공통 잡음)
        doc.select("header, footer, nav, aside").remove();

        // 3) 의미 없는 요소 제거
        doc.select("svg, img").remove();

        // 4) 텍스트 추출
        String text = doc.text();

        // 5) 공백 정리
        text = normalizeWhitespace(text);

        // 6) 너무 짧으면 제외
        if (text.length() < MIN_TEXT_LENGTH) return "";

        // 7) 스크랩 단위 길이 제한
        return clamp(text, MAX_TEXT_PER_SCRAP);
    }

    /**
     * 스크랩 여러 개 합쳐서 전체 총량 제한(프롬프트 조립 시 사용)
     */
    public static String clampTotal(String input) {
        if (input == null || input.isBlank()) return "";
        return clamp(input, MAX_TOTAL_TEXT);
    }

    private static String normalizeWhitespace(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String clamp(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "…";
    }

    public static String clampPerScrap(String string) {
        return clamp(string, MAX_TEXT_PER_SCRAP);
    }
}
