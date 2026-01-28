package com.moa.moa_backend.domain.digest.service;

import com.moa.moa_backend.domain.digest.dto.StageDigestResponse;
import com.moa.moa_backend.domain.digest.entity.StageDigest;
import com.moa.moa_backend.domain.digest.llm.StageDigestGeneratorPort;
import com.moa.moa_backend.domain.digest.repository.StageDigestRepository;
import com.moa.moa_backend.domain.project.entity.Project;
import com.moa.moa_backend.domain.project.repository.ProjectRepository;
import com.moa.moa_backend.domain.scrap.repository.ScrapDigestQueryRepository;
import com.moa.moa_backend.domain.scrap.repository.ScrapForDigestRepository;
import com.moa.moa_backend.domain.scrap.repository.projection.ScrapForDigestView;
import com.moa.moa_backend.global.error.ApiException;
import com.moa.moa_backend.global.error.ErrorCode;
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
public class StageDigestService {

    private static final int INPUT_SCRAPS_LIMIT = 20;

    private static final int DIGEST_VERSION = 1;
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final ProjectRepository projectRepository;
    private final StageDigestRepository stageDigestRepository;
    private final ScrapDigestQueryRepository scrapDigestQueryRepository;
    private final ScrapForDigestRepository scrapForDigestRepository;

    private final StageDigestGeneratorPort digestGenerator;
    private final StageDigestWriter stageDigestWriter;

    /**
     * (A+B) 테이블 수정 없이 refresh 결과를 메타로 노출하기 위한 메모리 캐시
     * - POST refresh에서 결과 저장
     * - GET digest에서 최근 결과 조회
     */
    private final DigestRefreshStatusCache refreshStatusCache;

    /**
     * 유니크 단위로 “갱신 중” 상태 관리 (단일 인스턴스에서만 유효)
     * - 동일 (userId, projectId, stage) refresh 동시 수행 방지
     */
    private final ConcurrentHashMap<String, Boolean> inFlight = new ConcurrentHashMap<>();

    private String key(Long userId, Long projectId, String stage) {
        String s = (stage == null) ? "" : stage.trim();
        return userId + ":" + projectId + ":" + s;
    }

    public StageDigestService(
            ProjectRepository projectRepository,
            StageDigestRepository stageDigestRepository,
            ScrapDigestQueryRepository scrapDigestQueryRepository,
            ScrapForDigestRepository scrapForDigestRepository,
            StageDigestGeneratorPort digestGenerator,
            StageDigestWriter stageDigestWriter,
            DigestRefreshStatusCache refreshStatusCache
    ) {
        this.projectRepository = projectRepository;
        this.stageDigestRepository = stageDigestRepository;
        this.scrapDigestQueryRepository = scrapDigestQueryRepository;
        this.scrapForDigestRepository = scrapForDigestRepository;
        this.digestGenerator = digestGenerator;
        this.stageDigestWriter = stageDigestWriter;
        this.refreshStatusCache = refreshStatusCache;
    }

    // =========================
    // 조회 API (LLM 호출 없음)
    // =========================
    @Transactional(readOnly = true)
    public StageDigestResponse getDigest(Long userId, Long projectId, String stage) {
        Project project = getOwnedProjectOrThrow(userId, projectId);

        // 최근 refresh 결과(있으면) meta.refresh로 내려주기
        StageDigestResponse.Refresh cachedRefresh = refreshStatusCache.getIfPresent(userId, projectId, stage);

        Optional<StageDigest> digestOpt =
                stageDigestRepository.findByUserIdAndProjectIdAndStage(userId, projectId, stage);

        Instant latestScrapInstant =
                scrapDigestQueryRepository.findLatestCapturedAt(userId, projectId, stage);

        OffsetDateTime latestScrapKst = toKst(latestScrapInstant);

        // digest 레코드가 아예 없으면
        // - 최신 스크랩이 존재한다면: “요약이 아직 없다”는 의미이므로 outdated=true로 두는 게 직관적
        // - 최신 스크랩이 없다면: 아무것도 없는 상태이므로 outdated=false
        if (digestOpt.isEmpty()) {
            boolean outdated = (latestScrapInstant != null);

            return new StageDigestResponse(
                    new StageDigestResponse.ProjectDto(projectId, project.getName()),
                    stage,
                    null,
                    new StageDigestResponse.Meta(
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

        StageDigest digest = digestOpt.get();
        String markdown = digest.getDigestText();
        boolean exists = (markdown != null && !markdown.isBlank());
        boolean outdated = exists && computeOutdated(digest.getSourceLastCapturedAt(), latestScrapInstant);

        return new StageDigestResponse(
                new StageDigestResponse.ProjectDto(projectId, project.getName()),
                stage,
                exists ? markdown : null,
                new StageDigestResponse.Meta(
                        exists,
                        outdated,
                        digest.getSourceLastCapturedAt(),
                        latestScrapKst,
                        digest.getUpdatedAt(),
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
    public StageDigestResponse refresh(Long userId, Long projectId, String stage) {

        String lockKey = key(userId, projectId, stage);

        // 마지막 시도 시각
        OffsetDateTime attemptedAt = DigestRefreshStatusCache.nowKst();

        // 이미 처리 중이면 409
        if (inFlight.putIfAbsent(lockKey, Boolean.TRUE) != null) {
            throw new ApiException(ErrorCode.DIGEST_REFRESH_IN_PROGRESS);
        }

        try {
            Project project = getOwnedProjectOrThrow(userId, projectId);

            Instant latestScrapInstant =
                    scrapDigestQueryRepository.findLatestCapturedAt(userId, projectId, stage);

            //스크랩 자체가 없으면: LLM 호출 불가 → SKIPPED 처리
            if (latestScrapInstant == null) {
                StageDigestResponse.Refresh refreshMeta = refreshSkipped(
                        "NO_SCRAPS",
                        "No scraps to digest.",
                        attemptedAt
                );
                refreshStatusCache.put(userId, projectId, stage, refreshMeta);

                return new StageDigestResponse(
                        new StageDigestResponse.ProjectDto(projectId, project.getName()),
                        stage,
                        null,
                        new StageDigestResponse.Meta(
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

            // =========================
            // 변경감지 -> 스킵
            // =========================
            Optional<StageDigest> existingOpt =
                    stageDigestRepository.findByUserIdAndProjectIdAndStage(userId, projectId, stage);

            if (existingOpt.isPresent()) {
                StageDigest existing = existingOpt.get();

                boolean upToDate =
                        existing.getSourceLastCapturedAt() != null &&
                                !latestScrapInstant.isAfter(existing.getSourceLastCapturedAt().toInstant());

                if (upToDate) {
                    log.info("[DIGEST] refresh skipped (up-to-date). userId={}, projectId={}, stage={}, latestScrap={}",
                            userId, projectId, stage, latestScrapKst);

                    String text = existing.getDigestText();
                    boolean exists = (text != null && !text.isBlank());

                    StageDigestResponse.Refresh refreshMeta = refreshSkipped(
                            "NOT_OUTDATED",
                            "Digest is up to date.",
                            attemptedAt
                    );
                    refreshStatusCache.put(userId, projectId, stage, refreshMeta);

                    return new StageDigestResponse(
                            new StageDigestResponse.ProjectDto(projectId, project.getName()),
                            stage,
                            exists ? text : null,
                            new StageDigestResponse.Meta(
                                    exists,
                                    false, // 최신이므로 outdated=false
                                    existing.getSourceLastCapturedAt(),
                                    latestScrapKst,
                                    existing.getUpdatedAt(),
                                    DIGEST_VERSION,
                                    refreshMeta
                            )
                    );
                }
            }

            // =========================
            // 스크랩 목록 조회 -> 입력 정규화
            // =========================
            List<ScrapForDigestView> scraps = scrapForDigestRepository.findRecentForDigest(
                    userId, projectId, stage, PageRequest.of(0, INPUT_SCRAPS_LIMIT)
            );

            List<ScrapForDigestView> normalized = scraps.stream()
                    .map(s -> new ScrapForDigestView(
                            s.scrapId(),
                            s.subtitle(),
                            s.memo(),
                            DigestInputNormalizer.normalizeRawHtml(s.rawHtml()),
                            s.capturedAt()
                    ))
                    .toList();

            // LLM 호출 전에 "의미 있는 입력" 여부 컷
            boolean hasAnyInput = normalized.stream().anyMatch(s ->
                    (s.rawHtml() != null && !s.rawHtml().isBlank()) ||
                            (s.subtitle() != null && !s.subtitle().isBlank()) ||
                            (s.memo() != null && !s.memo().isBlank())
            );

            if (!hasAnyInput) {
                log.debug("[DIGEST] refresh skipped (no meaningful scraps). userId={}, projectId={}, stage={}",
                        userId, projectId, stage);

                StageDigestResponse.Refresh refreshMeta = refreshSkipped(
                        "NO_MEANINGFUL_INPUT",
                        "No meaningful content to digest.",
                        attemptedAt
                );
                refreshStatusCache.put(userId, projectId, stage, refreshMeta);

                StageDigestResponse base = fallbackExistingDigest(
                        userId, projectId, project.getName(), stage,
                        latestScrapInstant, latestScrapKst,
                        refreshMeta
                );
                return base;
            }

            // =========================
            // LLM 호출 (트랜잭션 밖)
            // =========================
            final String markdown;
            try {
                markdown = digestGenerator.generateMarkdown(project.getName(), stage, normalized);
            } catch (Exception e) {
                log.error("[DIGEST] refresh failed. userId={}, projectId={}, stage={}, scraps={}",
                        userId, projectId, stage, normalized.size(), e);

                //실패 시 meta.refresh로 내려주기
                StageDigestResponse.Refresh refreshMeta = refreshFailed(e, attemptedAt);
                refreshStatusCache.put(userId, projectId, stage, refreshMeta);

                // digest는 기존 걸 주거나 null이 될 수 있음
                return fallbackExistingDigest(
                        userId, projectId, project.getName(), stage,
                        latestScrapInstant, latestScrapKst,
                        refreshMeta
                );
            }

            OffsetDateTime sourceLastCapturedAt = latestScrapKst;

            // =========================
            // DB write만 짧게 트랜잭션
            // =========================
            StageDigest saved = stageDigestWriter.upsertDigest(
                    userId, projectId, stage, markdown, sourceLastCapturedAt
            );

            StageDigestResponse.Refresh refreshMeta = refreshSuccess(attemptedAt);
            refreshStatusCache.put(userId, projectId, stage, refreshMeta);

            return successResponse(projectId, project.getName(), stage, markdown, saved, latestScrapKst, refreshMeta);

        } finally {
            inFlight.remove(lockKey);
        }
    }

    // =========================
    // Refresh Meta 생성
    // =========================

    private StageDigestResponse.Refresh refreshSuccess(OffsetDateTime attemptedAt) {
        return new StageDigestResponse.Refresh(
                "SUCCESS",
                null,
                null,
                null,
                attemptedAt
        );
    }

    private StageDigestResponse.Refresh refreshSkipped(String errorCode, String message, OffsetDateTime attemptedAt) {
        return new StageDigestResponse.Refresh(
                "SKIPPED",
                errorCode,
                message,
                null,
                attemptedAt
        );
    }

    /**
     * LLM 실패를 표준화해서 meta.refresh에 넣기
     * - 429(Rate limit)면 RATE_LIMITED로 내려서 프론트/운영이 즉시 알 수 있게
     * - retryAfterSeconds는 권장 대기시간 힌트로 사용 예정 (스케줄러)
     */
    private StageDigestResponse.Refresh refreshFailed(Throwable e, OffsetDateTime attemptedAt) {
        boolean rateLimited = isRateLimited(e);

        String errorCode = rateLimited ? "RATE_LIMITED" : "PROVIDER_ERROR";
        String message = rateLimited ? "LLM rate limited (429)." : "LLM refresh failed.";
        Integer retryAfterSeconds = rateLimited ? 60 : null; // 필요 없으면 null로 바꿔도 됨

        // 너무 긴 메시지는 잘라서 응답 폭발 방지
        String detail = shortenMessage(e.getMessage(), 200);
        if (detail != null && !detail.isBlank()) {
            message = message + " " + detail;
        }

        return new StageDigestResponse.Refresh(
                "FAILED",
                errorCode,
                message,
                retryAfterSeconds,
                attemptedAt
        );
    }

    /**
     * 429 판별 (가능한 넓게 잡음)
     * - WebClientResponseException.TooManyRequests(429)
     * - 예외 메시지에 429 포함
     * - cause 체인 탐색
     */
    private boolean isRateLimited(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof WebClientResponseException.TooManyRequests) return true;
            if (cur instanceof WebClientResponseException w) {
                if (w.getStatusCode().value() == 429) return true;
            }
            String msg = cur.getMessage();
            if (msg != null && msg.contains("429")) return true;
            cur = cur.getCause();
        }
        return false;
    }

    private String shortenMessage(String msg, int maxLen) {
        if (msg == null) return null;
        String trimmed = msg.trim();
        if (trimmed.length() <= maxLen) return trimmed;
        return trimmed.substring(0, maxLen) + "...";
    }

    // =========================
    // 공통 응답 헬퍼
    // =========================

    private StageDigestResponse successResponse(
            Long projectId, String projectName, String stage,
            String markdown, StageDigest saved, OffsetDateTime latestScrapKst,
            StageDigestResponse.Refresh refresh
    ) {
        return new StageDigestResponse(
                new StageDigestResponse.ProjectDto(projectId, projectName),
                stage,
                markdown,
                new StageDigestResponse.Meta(
                        true,
                        false,
                        saved.getSourceLastCapturedAt(),
                        latestScrapKst,
                        saved.getUpdatedAt(),
                        DIGEST_VERSION,
                        refresh
                )
        );
    }

    /**
     * 실패/스킵 시에도 “기존 digest가 있으면 그걸 돌려주되”
     * meta.refresh에 이번 시도 결과(FAILED/SKIPPED)를 같이 붙여준다.
     */
    private StageDigestResponse fallbackExistingDigest(
            Long userId, Long projectId, String projectName, String stage,
            Instant latestScrapInstant, OffsetDateTime latestScrapKst,
            StageDigestResponse.Refresh refresh
    ) {
        Optional<StageDigest> existingOpt =
                stageDigestRepository.findByUserIdAndProjectIdAndStage(userId, projectId, stage);

        if (existingOpt.isPresent()) {
            StageDigest existing = existingOpt.get();
            String existingText = existing.getDigestText();
            boolean exists = (existingText != null && !existingText.isBlank());
            boolean outdated = exists && computeOutdated(existing.getSourceLastCapturedAt(), latestScrapInstant);

            return new StageDigestResponse(
                    new StageDigestResponse.ProjectDto(projectId, projectName),
                    stage,
                    exists ? existingText : null,
                    new StageDigestResponse.Meta(
                            exists,
                            outdated,
                            existing.getSourceLastCapturedAt(),
                            latestScrapKst,
                            existing.getUpdatedAt(),
                            DIGEST_VERSION,
                            refresh
                    )
            );
        }

        // 기존 digest가 없으면 “요약 없음 + 이번 refresh 상태(refresh)”로 내려준다.
        // 최신 스크랩이 존재한다면 outdated=true가 더 자연스럽다.
        boolean outdated = (latestScrapInstant != null);

        return new StageDigestResponse(
                new StageDigestResponse.ProjectDto(projectId, projectName),
                stage,
                null,
                new StageDigestResponse.Meta(
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

    private boolean computeOutdated(OffsetDateTime sourceLastCapturedAt, Instant latestScrapInstant) {
        if (latestScrapInstant == null) return false;
        if (sourceLastCapturedAt == null) return true;
        return latestScrapInstant.isAfter(sourceLastCapturedAt.toInstant());
    }

    private OffsetDateTime toKst(Instant instant) {
        if (instant == null) return null;
        return instant.atOffset(ZoneOffset.UTC).withOffsetSameInstant(KST);
    }

    private Project getOwnedProjectOrThrow(Long userId, Long projectId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(ErrorCode.PROJECT_NOT_FOUND));

        if (!p.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return p;
    }
}

