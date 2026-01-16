package com.moa.moa_backend.domain.scrap.controller;

import com.moa.moa_backend.domain.scrap.service.MarkdownConvertService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MarkdownTestPageController {

    private final MarkdownConvertService markdownConvertService;

    public MarkdownTestPageController(MarkdownConvertService markdownConvertService) {
        this.markdownConvertService = markdownConvertService;
    }

    // GET 페이지: 브라우저로 접속해서 확인
    @GetMapping("/markdown/test")
    public String show(Model model) {
        String sampleHtml =
                "<h1>Header 1</h1>" +
                        "<p>Hello <b>World</b>!!</p>" +
                        "<ul><li>A</li><li>B</li></ul>";

        String markdown = markdownConvertService.convertHtmlToMarkdown(sampleHtml, "https://example.com/");

        model.addAttribute("html", sampleHtml);
        model.addAttribute("markdown", markdown);
        return "markdown-test"; // templates/markdown-test.html
    }

    // (선택) 페이지에서 HTML을 입력하고 변환 버튼 눌러 테스트
    @PostMapping("/markdown/test")
    public String convert(
            @RequestParam("html") String html,
            @RequestParam(value = "baseUrl", required = false, defaultValue = "") String baseUrl,
            Model model
    ) {
        String markdown = markdownConvertService.convertHtmlToMarkdown(html, baseUrl);

        model.addAttribute("html", html);
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("markdown", markdown);
        return "markdown-test";
    }
}
