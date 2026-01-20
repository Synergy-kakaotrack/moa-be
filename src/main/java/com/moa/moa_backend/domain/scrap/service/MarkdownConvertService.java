package com.moa.moa_backend.domain.scrap.service;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Component
public class MarkdownConvertService {

    private static final int MIN_HTML_HINT_LENGTH = 6;

    private final FlexmarkHtmlConverter flexmarkHtmlConverter;

    public MarkdownConvertService() {
        MutableDataSet options = new MutableDataSet();

        // NOTE: h1/h2를 Setext(====/----)가 아니라 ATX(#/##)로 출력
        options.set(FlexmarkHtmlConverter.SETEXT_HEADINGS, false);

        this.flexmarkHtmlConverter = FlexmarkHtmlConverter.builder(options).build();
    }

    public ConvertResult convert(String rawHtml) {
        if (rawHtml == null) {
            return new ConvertResult(null, "HTML");
        }

        String trimmed = rawHtml.trim();
        if (trimmed.isEmpty()) {
            return new ConvertResult(rawHtml, "HTML");
        }

        try {
            // NOTE: HTML이 아니면 변환하지 않고 원문 반환 (format=HTML)
            if (!isLikelyHtml(trimmed)) {
                return new ConvertResult(rawHtml, "HTML");
            }

            String normalizedHtml = normalizeHtmlFragment(trimmed);
            String markdown = flexmarkHtmlConverter.convert(normalizedHtml);

            if (markdown == null) {
                return new ConvertResult(rawHtml, "HTML");
            }

            String markdownTrimmed = markdown.trim();
            if (markdownTrimmed.isEmpty()) {
                return new ConvertResult(rawHtml, "HTML");
            }

            return new ConvertResult(markdownTrimmed, "MARKDOWN");
        } catch (RuntimeException ex) {
            return new ConvertResult(rawHtml, "HTML");
        }
    }

    public record ConvertResult(String content, String contentFormat) {}

    private static boolean isLikelyHtml(String text) {
        if (text.length() < MIN_HTML_HINT_LENGTH) {
            return false;
        }

        if (text.indexOf('<') < 0 || text.indexOf('>') < 0) {
            return false;
        }

        Document doc = Jsoup.parseBodyFragment(text);
        return doc.body() != null && doc.body().getAllElements().size() > 1;
    }

    private static String normalizeHtmlFragment(String html) {
        Document doc = Jsoup.parseBodyFragment(html);

        Document.OutputSettings outputSettings = new Document.OutputSettings();
        outputSettings.prettyPrint(false);
        doc.outputSettings(outputSettings);

        return doc.body() == null ? html : doc.body().html();
    }
}
