package com.moa.moa_backend.domain.scrap.service;

import com.aspose.html.converters.Converter;
import com.aspose.html.saving.MarkdownSaveOptions;
import com.aspose.html.saving.MarkdownFeatures;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

@Service
public class MarkdownConvertService {

    public String convertHtmlToMarkdown(String html, String baseUrl) {
        if (html == null || html.isBlank()) {
            throw new IllegalArgumentException("html is empty");
        }

        String safeBaseUrl = (baseUrl == null) ? "" : baseUrl;

        // 1) HTML 정규화 (b/i/span style 등 처리)
        String normalizedHtml = normalizeHtml(html);

        Path tempMd = null;
        try {
            tempMd = Files.createTempFile("aspose-", ".md");

            // 2) Aspose 변환 옵션: Strong/Emphasis 등 명시
            MarkdownSaveOptions options = MarkdownSaveOptions.getGit();
            options.setFeatures(
                    MarkdownFeatures.Header
                            | MarkdownFeatures.AutomaticParagraph
                            | MarkdownFeatures.List
                            | MarkdownFeatures.Link
                            | MarkdownFeatures.Emphasis
                            | MarkdownFeatures.Strong
                            | MarkdownFeatures.LineBreak
                            | MarkdownFeatures.InlineCode
                            | MarkdownFeatures.Image
                            | MarkdownFeatures.Strikethrough
                            | MarkdownFeatures.Table
                            | MarkdownFeatures.TaskList
            );

            Converter.convertHTML(
                    normalizedHtml,
                    safeBaseUrl,
                    options,
                    tempMd.toAbsolutePath().toString()
            );

            return Files.readString(tempMd, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Failed to convert HTML to Markdown", e);
        } finally {
            if (tempMd != null) {
                try { Files.deleteIfExists(tempMd); } catch (Exception ignored) {}
            }
        }
    }

    private String normalizeHtml(String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);

        // (A) 명시 태그 치환: <b> -> <strong>, <i> -> <em>
        doc.select("b").forEach(e -> e.tagName("strong"));
        doc.select("i").forEach(e -> e.tagName("em"));

        // (B) style 기반 치환: <span style="font-weight:700"> 등
        doc.select("span[style],font[style]").forEach(e -> {
            String style = e.attr("style").toLowerCase();

            // bold 케이스: font-weight:bold / 600~900
            if (style.contains("font-weight")) {
                boolean isBold = style.contains("bold") || style.matches(".*font-weight\\s*:\\s*(6\\d\\d|7\\d\\d|8\\d\\d|9\\d\\d).*");
                if (isBold) e.tagName("strong");
            }

            // italic 케이스
            if (style.contains("font-style") && style.contains("italic")) {
                e.tagName("em");
            }
        });

        // (C) 위험/불필요 요소 제거 (변환 품질 + 보안)
        doc.select("script,style,link,meta,noscript").remove();

        // (D) 이벤트 핸들러(onclick 등) 제거 + javascript: 링크 차단
        for (Element el : doc.getAllElements()) {
            var attrs = new ArrayList<>(el.attributes().asList());
            for (var attr : attrs) {
                String key = attr.getKey().toLowerCase();
                if (key.startsWith("on")) { // onclick, onload...
                    el.removeAttr(attr.getKey());
                }
            }

            if ("a".equals(el.tagName())) {
                String href = el.attr("href");
                if (href != null && href.toLowerCase().startsWith("javascript:")) {
                    el.attr("href", "#");
                }
            }
        }

        return doc.body().html();
    }
}
