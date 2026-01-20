package com.moa.moa_backend.domain.scrap.service;

import com.moa.moa_backend.domain.scrap.dto.ScrapDetailResponse.ContentFormat;
import com.moa.moa_backend.global.error.ApiException;
import com.moa.moa_backend.global.error.ErrorCode;
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

    public ConvertResult convert(Long userId, Long scrapId, String rawHtml) {
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
                // NOTE: 여기서 던져도 아래 catch에서 errorId + context 포함해 로깅됨
                throw new IllegalStateException("FORCE_FAIL marker detected");
            }

            String normalizedHtml = normalizeHtmlFragment(trimmed);
            String markdown = flexmarkHtmlConverter.convert(normalizedHtml);

            if (markdown == null || markdown.trim().isEmpty()) {
                return new ConvertResult(rawHtml, ContentFormat.HTML);
            }

            return new ConvertResult(markdown.trim(), ContentFormat.MARKDOWN);
        } catch (Exception ex) {
            // NOTE: 변환 실패 이벤트를 식별하기 위한 추적용 ID (운영에서 이 값으로 로그 검색/상호 참조)
            String errorId = java.util.UUID.randomUUID().toString();

            // NOTE: 원문 전체 로깅은 보안/용량 문제를 유발할 수 있으므로 앞부분만 잘라서 남김(최대 200자 등)
            String preview = rawHtml.length() <= 200 ? rawHtml : rawHtml.substring(0, 200);

            log.error(
                    // NOTE: Html -> Markdown 변환 실패 로그 (운영 관측/디버깅 목적)
                    // - errorId: 단일 실패 이벤트 식별/추적 키
                    // - userId/scrapId: 어떤 사용자/어떤 스크랩에서 실패했는지 즉시 확인 및 DB 재현 가능
                    // - length: 입력 크기 기반의 패턴 분석(대용량/특정 길이에서 실패 여부)
                    // - hash: 동일/유사 입력 반복 실패 여부를 빠르게 그룹핑
                    // - preview: 원문 전체 노출 없이 문제 태그/패턴을 추정하기 위한 입력 일부

                    // NOTE: (테스트 편의) 변환 실패를 재현할 때는 스택트레이스가 과도하게 길어져
                    //       로그 확인이 어려워질 수 있어, 현재는 예외 객체(ex) 출력(스택트레이스)을 의도적으로 생략한다.
                    //       실제 운영 장애 분석이 필요해지면 아래 ex 주석을 해제하여 스택트레이스를 함께 출력할 것.
                    "[HtmlToMarkdown] convert failed. errorId={}, userId={}, scrapId={}, length={}, hash={}, preview={}",
                    errorId,
                    userId,
                    scrapId,
                    rawHtml.length(),
                    Integer.toHexString(rawHtml.hashCode()),
                    preview
//                    , ex
            );

            throw new ApiException(
                    ErrorCode.SCRAP_CONTENT_CONVERSION_FAILED,
                    "스크랩 내용 변환에 실패했습니다. errorId=" + errorId
            );
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
