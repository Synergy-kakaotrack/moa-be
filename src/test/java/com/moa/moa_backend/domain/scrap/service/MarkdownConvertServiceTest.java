package com.moa.moa_backend.domain.scrap.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownConvertServiceTest {

    private final MarkdownConvertService service = new MarkdownConvertService();

    @Test
    void convertHtmlToMarkdown_shouldReturnMarkdown() {
        String html = "<h1>Header 1</h1><p>Hello World!!</p>";
        String md = service.convertHtmlToMarkdown(html, "https://example.com/");

        assertThat(md).contains("Header 1");
        assertThat(md).contains("Hello World");
    }
}
