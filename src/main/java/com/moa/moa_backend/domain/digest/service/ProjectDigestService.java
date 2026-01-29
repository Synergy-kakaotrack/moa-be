package com.moa.moa_backend.domain.digest.service;

import com.moa.moa_backend.domain.digest.dto.ProjectDigestResponse;
import com.moa.moa_backend.domain.digest.entity.DigestKind;
import com.moa.moa_backend.domain.digest.entity.ProjectDigest;
import com.moa.moa_backend.domain.digest.llm.ProjectDigestGeneratorPort;
import com.moa.moa_backend.domain.digest.repository.ProjectDigestRepository;
import com.moa.moa_backend.domain.project.entity.Project;
import com.moa.moa_backend.domain.project.repository.ProjectRepository;
import com.moa.moa_backend.domain.scrap.repository.ProjectScrapDigestQueryRepository;
import com.moa.moa_backend.domain.scrap.repository.ProjectScrapForDigestRepository;
import com.moa.moa_backend.domain.scrap.repository.projection.ScrapForDigestView;
import com.moa.moa_backend.global.error.ApiException;
import com.moa.moa_backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectDigestService {

    private static final int INPUT_SCRAPS_LIMIT = 50;
    private static final int DIGEST_VERSION = 1;
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final ProjectRepository projectRepository;
    private final ProjectDigestRepository projectDigestRepository;

    private final ProjectScrapDigestQueryRepository projectScrapDigestQueryRepository;
    private final ProjectScrapForDigestRepository projectScrapForDigestRepository;

    private final ProjectDigestGeneratorPort digestGenerator;
    private final ProjectDigestWriter projectDigestWriter;
    private final ProjectDigestRefreshStatusCache refreshStatusCache;

    /**
     * 유니크 단위로 “갱신 중” 상태 관리 (단일 인스턴스에서만 유효)
     * - 동일 (userId, projectId) refresh 동시 수행 방지
     */
    private final ConcurrentHashMap<String, Boolean> inFlight = new ConcurrentHashMap<>();

    private String key(Long userId, Long projectId) {
        return userId + ":" + projectId;
    }

    // =========================
    // 조회 API (LLM 호출 없음)
    // =========================
    @Transactional(readOnly = true)
    public ProjectDigestResponse getDigest(Long userId, Long projectId) {
        Project project = getOwnedProjectOrThrow(userId, projectId);

        ProjectDigestResponse.Refresh cachedRefresh =
                refreshStatusCache.getIfPresent(userId, projectId);

        Optional<ProjectDigest> digestOpt =
                projectDigestRepository.findByUserIdAndProjectId(userId, projectId);

        Instant latestScrapInstant =
                projectScrapDigestQueryRepository.findLatestCapturedAt(userId, projectId);

        OffsetDateTime latestScrapKst = toKst(latestScrapInstant);

        if (digestOpt.isEmpty()) {
            boolean outdated = (latestScrapInstant != null);
            return new ProjectDigestResponse(
                    new ProjectDigestResponse.ProjectDto(projectId, project.getName()),
                    DigestKind.DEFAULT,
                    null,
                    new ProjectDigestResponse.Meta(
                            false,
                            outdated,
                            null,
                            latestScrapKst,
                            null,
                            DIGEST_VERSION,
                            cachedRefresh
                    )
            );
        }

        ProjectDigest digest = digestOpt.get();
        boolean exists = hasText(digest.getDigestText());
        boolean outdated = exists && computeOutdated(digest.getSourceLastUpdatedAt(), latestScrapInstant);

        return new ProjectDigestResponse(
                new ProjectDigestResponse.ProjectDto(projectId, project.getName()),
                digest.getDigestKind(),
                exists ? digest.getDigestText() : null,
                new ProjectDigestResponse.Meta(
                        exists,
                        outdated,
                        toKst(digest.getSourceLastUpdatedAt()),
                        latestScrapKst,
                        toKst(digest.getUpdatedAt()),
                        DIGEST_VERSION,
                        cachedRefresh
                )
        );
    }

    // =========================
    // 갱신 API (LLM 호출 있음)
    // - 트랜잭션 없음
    // - LLM은 트랜잭션 밖
    // - DB upsert만 writer에서 짧게 트랜잭션
    // =========================
    public ProjectDigestResponse refresh(Long userId, Long projectId, String prompt) {
        String lockKey = key(userId, projectId);
        OffsetDateTime attemptedAt = ProjectDigestRefreshStatusCache.nowKst();

        if (inFlight.putIfAbsent(lockKey, Boolean.TRUE) != null) {
            throw new ApiException(ErrorCode.DIGEST_REFRESH_IN_PROGRESS);
        }

        try {
            Project project = getOwnedProjectOrThrow(userId, projectId);

            DigestKind kind = (prompt == null || prompt.isBlank())
                    ? DigestKind.DEFAULT
                    : DigestKind.CUSTOM;

            Instant latestScrapInstant =
                    projectScrapDigestQueryRepository.findLatestCapturedAt(userId, projectId);

            // 스크랩 자체가 없으면: LLM 호출 불가 → SKIPPED
            if (latestScrapInstant == null) {
                ProjectDigestResponse.Refresh refreshMeta = refreshSkipped(
                        "NO_SCRAPS", "No scraps to digest.", attemptedAt
                );
                refreshStatusCache.put(userId, projectId, refreshMeta);

                return new ProjectDigestResponse(
                        new ProjectDigestResponse.ProjectDto(projectId, project.getName()),
                        kind,
                        null,
                        new ProjectDigestResponse.Meta(
                                false,
                                false,
                                null,
                                null,
                                null,
                                DIGEST_VERSION,
                                refreshMeta
                        )
                );
            }

            OffsetDateTime latestScrapKst = toKst(latestScrapInstant);

            Optional<ProjectDigest> existingOpt =
                    projectDigestRepository.findByUserIdAndProjectId(userId, projectId);

            if (existingOpt.isPresent()) {
                ProjectDigest existing = existingOpt.get();

                boolean hasDigestText = hasText(existing.getDigestText());
                boolean upToDate = isUpToDate(existing, latestScrapInstant);
                boolean same = sameCondition(existing, kind, prompt);

                // - 기존 digest 텍스트가 있고
                // - 스크랩 최신이고
                // - 요청 조건(kind/prompt)도 동일하면
                // => SKIPPED
                if (hasDigestText && upToDate && same) {
                    ProjectDigestResponse.Refresh refreshMeta = refreshSkipped(
                            "NOT_OUTDATED", "Digest is up to date.", attemptedAt
                    );
                    refreshStatusCache.put(userId, projectId, refreshMeta);
                    return existingView(project, existing, latestScrapKst, refreshMeta);
                }
            }

            // =========================
            // 입력 스크랩 조회 + 정규화
            // =========================
            List<ScrapForDigestView> scraps = projectScrapForDigestRepository.findRecentForDigest(
                    userId, projectId, PageRequest.of(0, INPUT_SCRAPS_LIMIT)
            );

            List<ScrapForDigestView> normalized = scraps.stream()
                    .map(s -> new ScrapForDigestView(
                            s.scrapId(),
                            s.stage(),
                            s.subtitle(),
                            s.memo(),
                            DigestInputNormalizer.normalizeRawHtml(s.rawHtml()),
                            s.capturedAt()
                    ))
                    .toList();

            boolean hasAnyInput = normalized.stream().anyMatch(s ->
                    hasText(s.rawHtml()) || hasText(s.subtitle()) || hasText(s.memo())
            );

            if (!hasAnyInput) {
                ProjectDigestResponse.Refresh refreshMeta = refreshSkipped(
                        "NO_MEANINGFUL_INPUT",
                        "No meaningful content to digest.",
                        attemptedAt
                );
                refreshStatusCache.put(userId, projectId, refreshMeta);

                return fallbackExisting(project, latestScrapInstant, latestScrapKst, refreshMeta, existingOpt);
            }

            // 이번 요약이 반영한 기준 시각(= 최신 스크랩 시각)
            Instant sourceLastCapturedAt = latestScrapInstant;

            // =========================
            // LLM 호출
            // =========================
            final String markdown;
            try {
                markdown = digestGenerator.generateMarkdown(
                        project.getName(),
                        kind,
                        (kind == DigestKind.CUSTOM) ? prompt : null,
                        normalized
                );
            } catch (Exception e) {
                log.error("[PROJECT_DIGEST] refresh failed. userId={}, projectId={}, scraps={}",
                        userId, projectId, normalized.size(), e);

                ProjectDigestResponse.Refresh refreshMeta = refreshFailed(e, attemptedAt);
                refreshStatusCache.put(userId, projectId, refreshMeta);

                return fallbackExisting(project, latestScrapInstant, latestScrapKst, refreshMeta, existingOpt);
            }

            ProjectDigest saved = projectDigestWriter.upsertDigest(
                    userId,
                    projectId,
                    kind,
                    (kind == DigestKind.CUSTOM) ? prompt : null,
                    markdown,
                    sourceLastCapturedAt
            );

            ProjectDigestResponse.Refresh refreshMeta = refreshSuccess(attemptedAt);
            refreshStatusCache.put(userId, projectId, refreshMeta);

            return new ProjectDigestResponse(
                    new ProjectDigestResponse.ProjectDto(projectId, project.getName()),
                    saved.getDigestKind(),
                    markdown,
                    new ProjectDigestResponse.Meta(
                            true,
                            false,
                            toKst(saved.getSourceLastUpdatedAt()),
                            latestScrapKst,
                            toKst(saved.getUpdatedAt()),
                            DIGEST_VERSION,
                            refreshMeta
                    )
            );

        } finally {
            inFlight.remove(lockKey);
        }
    }

    // =========================
    // refresh meta
    // =========================
    private ProjectDigestResponse.Refresh refreshSuccess(OffsetDateTime attemptedAt) {
        return new ProjectDigestResponse.Refresh("SUCCESS", null, null, null, attemptedAt);
    }

    private ProjectDigestResponse.Refresh refreshSkipped(String errorCode, String message, OffsetDateTime attemptedAt) {
        return new ProjectDigestResponse.Refresh("SKIPPED", errorCode, message, null, attemptedAt);
    }

    private ProjectDigestResponse.Refresh refreshFailed(Throwable e, OffsetDateTime attemptedAt) {
        boolean rateLimited = isRateLimited(e);

        String errorCode = rateLimited ? "RATE_LIMITED" : "PROVIDER_ERROR";
        String message = rateLimited ? "LLM rate limited (429)." : "LLM refresh failed.";
        Integer retryAfterSeconds = rateLimited ? 60 : null;

        String detail = shortenMessage(e.getMessage(), 200);
        if (hasText(detail)) message = message + " " + detail;

        return new ProjectDigestResponse.Refresh("FAILED", errorCode, message, retryAfterSeconds, attemptedAt);
    }

    private boolean isRateLimited(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof WebClientResponseException.TooManyRequests) return true;
            if (cur instanceof WebClientResponseException w && w.getStatusCode().value() == 429) return true;
            String msg = cur.getMessage();
            if (msg != null && msg.contains("429")) return true;
            cur = cur.getCause();
        }
        return false;
    }

    private String shortenMessage(String msg, int maxLen) {
        if (msg == null) return null;
        String t = msg.trim();
        return (t.length() <= maxLen) ? t : t.substring(0, maxLen) + "...";
    }

    // =========================
    // view / fallback
    // =========================
    private ProjectDigestResponse existingView(
            Project project,
            ProjectDigest digest,
            OffsetDateTime latestScrapKst,
            ProjectDigestResponse.Refresh refresh
    ) {
        boolean exists = hasText(digest.getDigestText());
        return new ProjectDigestResponse(
                new ProjectDigestResponse.ProjectDto(project.getId(), project.getName()),
                digest.getDigestKind(),
                exists ? digest.getDigestText() : null,
                new ProjectDigestResponse.Meta(
                        exists,
                        false,
                        toKst(digest.getSourceLastUpdatedAt()),
                        latestScrapKst,
                        toKst(digest.getUpdatedAt()),
                        DIGEST_VERSION,
                        refresh
                )
        );
    }

    private ProjectDigestResponse fallbackExisting(
            Project project,
            Instant latestScrapInstant,
            OffsetDateTime latestScrapKst,
            ProjectDigestResponse.Refresh refresh,
            Optional<ProjectDigest> existingOpt
    ) {
        if (existingOpt.isPresent()) {
            ProjectDigest existing = existingOpt.get();
            boolean exists = hasText(existing.getDigestText());
            boolean outdated = exists && computeOutdated(existing.getSourceLastUpdatedAt(), latestScrapInstant);

            return new ProjectDigestResponse(
                    new ProjectDigestResponse.ProjectDto(project.getId(), project.getName()),
                    existing.getDigestKind(),
                    exists ? existing.getDigestText() : null,
                    new ProjectDigestResponse.Meta(
                            exists,
                            outdated,
                            toKst(existing.getSourceLastUpdatedAt()),
                            latestScrapKst,
                            toKst(existing.getUpdatedAt()),
                            DIGEST_VERSION,
                            refresh
                    )
            );
        }

        boolean outdated = (latestScrapInstant != null);

        return new ProjectDigestResponse(
                new ProjectDigestResponse.ProjectDto(project.getId(), project.getName()),
                DigestKind.DEFAULT,
                null,
                new ProjectDigestResponse.Meta(
                        false,
                        outdated,
                        null,
                        latestScrapKst,
                        null,
                        DIGEST_VERSION,
                        refresh
                )
        );
    }

    private boolean computeOutdated(Instant sourceLastUpdatedAt, Instant latestScrapInstant) {
        if (latestScrapInstant == null) return false;
        if (sourceLastUpdatedAt == null) return true;
        return latestScrapInstant.isAfter(sourceLastUpdatedAt);
    }

    private OffsetDateTime toKst(Instant instant) {
        if (instant == null) return null;
        return instant.atOffset(ZoneOffset.UTC).withOffsetSameInstant(KST);
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private Project getOwnedProjectOrThrow(Long userId, Long projectId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(ErrorCode.PROJECT_NOT_FOUND));

        if (!p.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return p;
    }

    private boolean isUpToDate(ProjectDigest existing, Instant latestScrapInstant) {
        if (latestScrapInstant == null) return true; // scraps 없으면 위에서 이미 SKIPPED 처리 중이니 의미상 true
        Instant src = existing.getSourceLastUpdatedAt();
        if (src == null) return false;
        return !latestScrapInstant.isAfter(src);
    }

    private boolean sameCondition(ProjectDigest existing, DigestKind requestedKind, String requestedPrompt) {
        if (existing.getDigestKind() != requestedKind) return false;

        if (requestedKind == DigestKind.DEFAULT) {
            return true; // kind만 같으면 동일 조건
        }

        // CUSTOM이면 prompt까지 같아야 동일 조건
        String oldPrompt = normalizePrompt(existing.getPromptText()); // 필드명에 맞게 수정
        String newPrompt = normalizePrompt(requestedPrompt);
        return oldPrompt.equals(newPrompt);
    }

    private String normalizePrompt(String p) {
        return (p == null) ? "" : p.trim();
    }

}
