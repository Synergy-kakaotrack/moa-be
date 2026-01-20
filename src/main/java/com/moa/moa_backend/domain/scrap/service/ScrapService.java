package com.moa.moa_backend.domain.scrap.service;

import com.moa.moa_backend.domain.draft.dto.DraftCommitRequest;
import com.moa.moa_backend.domain.draft.entity.DraftStage;
import com.moa.moa_backend.domain.draft.entity.RecMethod;
import com.moa.moa_backend.domain.project.repository.ProjectRepository;
import com.moa.moa_backend.domain.scrap.dto.ScrapDetailResponse;
import com.moa.moa_backend.domain.scrap.dto.ScrapListResponse;
import com.moa.moa_backend.domain.scrap.dto.ScrapRecentContextResponse;
import com.moa.moa_backend.domain.scrap.entity.Scrap;
import com.moa.moa_backend.domain.scrap.repository.ScrapRepository;
import com.moa.moa_backend.domain.scrap.service.MarkdownConvertService;
import com.moa.moa_backend.global.error.ApiException;
import com.moa.moa_backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Transactional
@RequiredArgsConstructor
@Service
public class ScrapService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final ScrapRepository scrapRepository;
    private final ProjectRepository projectRepository;
    private final MarkdownConvertService markdownConvertService;

    // =========================
    // Create (Draft commit -> Scrap)
    // =========================
    public Long createFromDraftCommit(
            Long userId,
            DraftCommitRequest req,
            RecMethod recMethod,
            Instant capturedAt
    ) {
        if (req.projectId() == null || !projectRepository.existsByIdAndUserId(req.projectId(), userId)) {
            throw new ApiException(ErrorCode.PROJECT_NOT_FOUND);
        }

        if (req.stage() == null || !DraftStage.isValid(req.stage())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "유효하지 않은 작업 단계입니다: " + req.stage());
        }

        String rawHtml = normalizeRequired(req.rawHtml(), "rawHtml");
        String subtitle = normalizeRequired(req.subtitle(), "subtitle");
        String aiSource = normalizeRequired(req.aiSource(), "aiSource");
        String aiSourceUrl = normalizeRequired(req.aiSourceUrl(), "aiSourceUrl");

        boolean userRecProject = Boolean.TRUE.equals(req.userRecProject());
        boolean userRecStage = Boolean.TRUE.equals(req.userRecStage());
        boolean userRecSubtitle = Boolean.TRUE.equals(req.userRecSubtitle());

        Scrap scrap = Scrap.create(
                req.projectId(),
                userId,
                rawHtml,
                subtitle,
                req.stage(),
                req.memo(),
                aiSource,
                aiSourceUrl,
                userRecProject,
                userRecStage,
                userRecSubtitle,
                capturedAt,
                recMethod
        );

        return scrapRepository.save(scrap).getId();
    }

    // =========================
    // Read: List (cursor paging)
    // =========================
    @Transactional(readOnly = true)
    public ScrapListResponse getScrapList(
            Long userId,
            Long projectId,
            String stage,
            String cursor,
            Integer limit
    ) {
        if (!projectRepository.existsByIdAndUserId(projectId, userId)) {
            throw new ApiException(ErrorCode.PROJECT_NOT_FOUND);
        }
        if (projectId == null || projectId <= 0) {
            throw new ApiException(ErrorCode.INVALID_QUERY_PARAM, "projectId가 올바르지 않습니다.");
        }
        // Validate project exists and belongs to user
        if (!projectRepository.existsByIdAndUserId(projectId, userId)) {
            throw new ApiException(ErrorCode.PROJECT_NOT_FOUND);
        }
        if (stage == null || stage.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_QUERY_PARAM, "stage가 올바르지 않습니다.");
        }
        // 선택: stage를 enum 기준으로 강제 검증하고 싶으면 유지
        if (!DraftStage.isValid(stage)) {
            throw new ApiException(ErrorCode.INVALID_QUERY_PARAM, "유효하지 않은 stage 입니다: " + stage);
        }

        int pageSize = normalizeLimit(limit);
        PageRequest pageable = PageRequest.of(0, pageSize + 1); // nextCursor 판단 위해 +1

        ScrapCursorCodec.Cursor decoded = ScrapCursorCodec.decodeOrNull(cursor);

        List<Scrap> rows;
        if (decoded == null) {
            // cursor 없음: 첫 페이지 쿼리 (NULL 파라미터 자체가 없음 → PG 타입 에러 방지)
            rows = scrapRepository.findFirstPage(userId, projectId, stage, pageable);
        } else {
            Instant lastCapturedAt = decoded.lastCapturedAt();
            Long lastScrapId = decoded.lastScrapId();

            if (lastCapturedAt == null || lastScrapId == null) {
                throw new ApiException(ErrorCode.INVALID_QUERY_PARAM, "cursor 형식이 올바르지 않습니다.");
            }

            rows = scrapRepository.findNextPage(
                    userId,
                    projectId,
                    stage,
                    lastCapturedAt,
                    lastScrapId,
                    pageable
            );
        }

        boolean hasNext = rows.size() > pageSize;
        List<Scrap> page = hasNext ? rows.subList(0, pageSize) : rows;

        List<ScrapListResponse.Item> items = page.stream()
                .map(s -> new ScrapListResponse.Item(
                        s.getId(),
                        s.getProjectId(),
                        s.getStage(),
                        s.getSubtitle(),
                        s.getMemo(),
                        s.getCapturedAt()
                ))
                .toList();

        String nextCursor = null;
        if (hasNext && !page.isEmpty()) {
            Scrap last = page.get(page.size() - 1);
            nextCursor = ScrapCursorCodec.encode(last.getCapturedAt(), last.getId());
        }

        return new ScrapListResponse(items, nextCursor);
    }

    // =========================
    // Read: Detail
    // =========================
    @Transactional(readOnly = true)
    public ScrapDetailResponse getScrapDetail(Long userId, Long scrapId) {
        if (scrapId == null || scrapId <= 0) {
            throw new ApiException(ErrorCode.INVALID_QUERY_PARAM, "scrapId가 올바르지 않습니다.");
        }

        Scrap s = scrapRepository.findByIdAndUserId(scrapId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCRAP_NOT_FOUND));

        MarkdownConvertService.ConvertResult result = markdownConvertService.convert(userId, scrapId, s.getRawHtml());

        return new ScrapDetailResponse(
                s.getId(),
                s.getProjectId(),
                s.getStage(),
                s.getSubtitle(),
                s.getMemo(),
                result.content(),
                result.contentFormat(),
                s.getAiSource(),
                s.getAiSourceUrl(),
                s.getCapturedAt()
        );
    }

    // =========================
    // Read: Recent context
    // =========================
    @Transactional(readOnly = true)
    public ScrapRecentContextResponse getRecentContext(Long userId) {
        List<ScrapRecentContextResponse.Item> items =
                scrapRepository.findRecentContext(userId).stream()
                        .map(r -> new ScrapRecentContextResponse.Item(
                                r.getProjectId(),
                                r.getProjectName(),
                                r.getLastStage(),
                                r.getLastCapturedAt()
                        ))
                        .toList();

        return new ScrapRecentContextResponse(items);
    }

    // =========================
    // Helpers
    // =========================
    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    private static String normalizeRequired(String v, String fieldName) {
        if (v == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, fieldName + "은(는) 필수입니다.");
        }
        String t = v.trim();
        if (t.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, fieldName + "은(는) 빈 문자열일 수 없습니다.");
        }
        return t;
    }
}
