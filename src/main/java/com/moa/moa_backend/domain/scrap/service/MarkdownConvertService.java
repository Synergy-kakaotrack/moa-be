package com.moa.moa_backend.domain.scrap.service;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
public class MarkdownConvertService {

    // NOTE: HTML 태그 여부를 빠르게 거르는 최소 길이 (너무 짧은 문자열의 오탐을 줄임)
    private static final int MIN_HTML_HINT_LENGTH = 6;

    private final FlexmarkHtmlConverter flexmarkHtmlConverter;

    public MarkdownConvertService() {
        // NOTE: 필요 시 옵션(헤딩 스타일, 리스트 처리 등)을 여기서 일괄 적용 가능
        MutableDataSet options = new MutableDataSet();

        // NOTE: h1/h2를 Setext(====/----)가 아니라 ATX(#/##)로 출력
        options.set(FlexmarkHtmlConverter.SETEXT_HEADINGS, false);

        this.flexmarkHtmlConverter = FlexmarkHtmlConverter.builder(options).build();
    }

    public String toMarkdownIfHtml(String rawHtml) {
        if (rawHtml == null) {
            return null;
        }

        String trimmed = rawHtml.trim();
        if (trimmed.isEmpty()) {
            return rawHtml;
        }

        if (!isLikelyHtml(trimmed)) {
            return rawHtml;
        }

        String normalizedHtml = normalizeHtmlFragment(trimmed);
        String markdown = flexmarkHtmlConverter.convert(normalizedHtml);

        if (markdown == null) {
            return rawHtml;
        }

        String markdownTrimmed = markdown.trim();
        return markdownTrimmed.isEmpty() ? rawHtml : markdownTrimmed;
    }

    private static boolean isLikelyHtml(String text) {
        if (text.length() < MIN_HTML_HINT_LENGTH) {
            return false;
        }

        // NOTE: '<' 또는 '>'가 아예 없으면 HTML로 보기 어려움
        if (text.indexOf('<') < 0 || text.indexOf('>') < 0) {
            return false;
        }

        // NOTE: Jsoup 파싱 결과로 실제 Element가 존재하는지 확인
        Document doc = Jsoup.parseBodyFragment(text);
        return doc.body() != null && doc.body().getAllElements().size() > 1;
    }

    private static String normalizeHtmlFragment(String html) {
        Document doc = Jsoup.parseBodyFragment(html);

        // NOTE: prettyPrint를 끄면 불필요한 줄바꿈/들여쓰기 삽입을 줄임
        Document.OutputSettings outputSettings = new Document.OutputSettings();
        outputSettings.prettyPrint(false);
        doc.outputSettings(outputSettings);

        return doc.body() == null ? html : doc.body().html();
    }
}