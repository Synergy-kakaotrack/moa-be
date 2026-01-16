package com.moa.moa_backend.domain.scrap.controller;

import com.moa.moa_backend.domain.scrap.service.MarkdownConvertService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/markdown")
public class MarkdownConvertController {

    private final MarkdownConvertService markdownConvertService;

    public MarkdownConvertController(MarkdownConvertService markdownConvertService) {
        this.markdownConvertService = markdownConvertService;
    }

    @PostMapping("/convert")
    public Map<String, String> convert(@RequestBody Map<String, Object> body) {
        String html = body.get("html") == null ? null : body.get("html").toString();
        String baseUrl = body.get("baseUrl") == null ? "" : body.get("baseUrl").toString();

        String markdown = markdownConvertService.convertHtmlToMarkdown(html, baseUrl);
        return Map.of("markdown", markdown);
    }
}
