package com.moa.moa_backend.domain.scrap.service;

import com.moa.moa_backend.domain.scrap.dto.ScrapDetailResponse.ContentFormat;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.util.data.MutableDataSet;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MarkdownConvertService {

    private static final int MIN_HTML_HINT_LENGTH = 6;

    private final FlexmarkHtmlConverter flexmarkHtmlConverter;

    public MarkdownConvertService() {
        MutableDataSet options = new MutableDataSet();
        options.set(FlexmarkHtmlConverter.SETEXT_HEADINGS, false);
        this.flexmarkHtmlConverter = FlexmarkHtmlConverter.builder(options).build();
    }

    public ConvertResult convert(String rawHtml) {
        if (rawHtml == null) {
            return new ConvertResult(null, ContentFormat.NULL);
        }

        String trimmed = rawHtml.trim();
        if (trimmed.isEmpty()) {
            return new ConvertResult(rawHtml, ContentFormat.HTML);
        }

        if (!isLikelyHtml(trimmed)) {
            return new ConvertResult(rawHtml, ContentFormat.HTML);
        }

        try {
            // NOTE: 테스트용 강제 실패 마커
            if (rawHtml.contains("<!--FORCE_FAIL-->")) {
                throw new RuntimeException("forced fail for test");
            }

            String normalizedHtml = normalizeHtmlFragment(trimmed);
            String markdown = flexmarkHtmlConverter.convert(normalizedHtml);

            if (markdown == null || markdown.trim().isEmpty()) {
                return new ConvertResult(rawHtml, ContentFormat.HTML);
            }

            return new ConvertResult(markdown.trim(), ContentFormat.MARKDOWN);
        } catch (Exception ex) {
            log.warn(
                    "[MarkdownConvertService] HTML->Markdown convert failed. length={}, hash={}",
                    rawHtml.length(),
                    Integer.toHexString(rawHtml.hashCode()),
                    ex
            );
            return new ConvertResult(rawHtml, ContentFormat.FAIL);
        }
    }

    public record ConvertResult(String content, ContentFormat contentFormat) {}

    private static boolean isLikelyHtml(String text) {
        if (text.length() < MIN_HTML_HINT_LENGTH) return false;
        if (text.indexOf('<') < 0 || text.indexOf('>') < 0) return false;

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
