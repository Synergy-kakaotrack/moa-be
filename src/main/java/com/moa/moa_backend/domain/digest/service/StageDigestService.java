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

    // 유니크 단위로 갱신 중 상태 관리 (단일 인스턴스에서만 유효)
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
            StageDigestWriter stageDigestWriter
    ) {
        this.projectRepository = projectRepository;
        this.stageDigestRepository = stageDigestRepository;
        this.scrapDigestQueryRepository = scrapDigestQueryRepository;
        this.scrapForDigestRepository = scrapForDigestRepository;
        this.digestGenerator = digestGenerator;
        this.stageDigestWriter = stageDigestWriter;
    }

    // =========================
    // 조회 API (LLM 호출 없음)
    // =========================
    @Transactional(readOnly = true)
    public StageDigestResponse getDigest(Long userId, Long projectId, String stage) {
        Project project = getOwnedProjectOrThrow(userId, projectId);

        Optional<StageDigest> digestOpt =
                stageDigestRepository.findByUserIdAndProjectIdAndStage(userId, projectId, stage);

        Instant latestScrapInstant =
                scrapDigestQueryRepository.findLatestCapturedAt(userId, projectId, stage);

        OffsetDateTime latestScrapKst = toKst(latestScrapInstant);

        if (digestOpt.isEmpty()) {
            return new StageDigestResponse(
                    new StageDigestResponse.ProjectDto(projectId, project.getName()),
                    stage,
                    null,
                    new StageDigestResponse.Meta(
                            false,
                            false,
                            null,
                            latestScrapKst,
                            null,
                            DIGEST_VERSION
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
                        DIGEST_VERSION
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

        // 이미 처리 중이면 409
        if (inFlight.putIfAbsent(lockKey, Boolean.TRUE) != null) {
            throw new ApiException(ErrorCode.DIGEST_REFRESH_IN_PROGRESS);
        }

        try {
            Project project = getOwnedProjectOrThrow(userId, projectId);

            Instant latestScrapInstant =
                    scrapDigestQueryRepository.findLatestCapturedAt(userId, projectId, stage);

            if (latestScrapInstant == null) {
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
                                DIGEST_VERSION
                        )
                );
            }

            OffsetDateTime latestScrapKst = toKst(latestScrapInstant);

            /**
             * 변경 감지 -> 스킵
             *
             * 목적 :
             * - 스크랩이 변했으 때만 llm 호출/db업데이트 수행
             * - 변하지 않으면 여기서 바로 기존 digest 반환하여 비용과 부하 차단
             *
             */
            Optional<StageDigest> existingOpt =
                    stageDigestRepository.findByUserIdAndProjectIdAndStage(userId, projectId, stage);

            if (existingOpt.isPresent()) {
                StageDigest existing = existingOpt.get();

                /**
                 * upToDate 조건 설명:
                 * - digest가 마지막으로 반영했던 스크랩 기준시각이 있고 현재 스크랩 최신시각이
                 * - 그 이후가 아니라면 "변경 없음"으로 간주 : digest는 이미 최신 상태
                 */
                boolean upToDate =
                        existing.getSourceLastCapturedAt() != null &&
                                !latestScrapInstant.isAfter(existing.getSourceLastCapturedAt().toInstant());

                if (upToDate) {
                    log.info("[DIGEST] refresh skipped (up-to-date). userId={}, projectId={}, stage={}, latestScrap={}",
                            userId, projectId, stage, latestScrapKst);

                    String text = existing.getDigestText();
                    boolean exists = (text != null && !text.isBlank());

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
                                    DIGEST_VERSION
                            )
                    );
                }
            }

            /**
             * upToDate가 아니면(스크랩이 바뀌었으면) 여기부터 실제 갱신 수행
             * 스크랩 목록 조회 -> 입력 정규화 -> (의미 있는 입력 확인) -> LLM -> DB upsert
             */
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

                return fallbackExistingDigest(
                        userId, projectId, project.getName(), stage,
                        latestScrapInstant, latestScrapKst
                );
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

                return fallbackExistingDigest(
                        userId, projectId, project.getName(), stage,
                        latestScrapInstant, latestScrapKst
                );
            }

            OffsetDateTime sourceLastCapturedAt = latestScrapKst;

            // =========================
            // DB write만 짧게 트랜잭션
            // =========================
            StageDigest saved = stageDigestWriter.upsertDigest(
                    userId, projectId, stage, markdown, sourceLastCapturedAt
            );

            return successResponse(projectId, project.getName(), stage, markdown, saved, latestScrapKst);

        } finally {
            inFlight.remove(lockKey);
        }
    }

    // =========================
    // 공통 응답 헬퍼
    // =========================
    private StageDigestResponse successResponse(
            Long projectId, String projectName, String stage,
            String markdown, StageDigest saved, OffsetDateTime latestScrapKst
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
                        DIGEST_VERSION
                )
        );
    }

    private StageDigestResponse fallbackExistingDigest(
            Long userId, Long projectId, String projectName, String stage,
            Instant latestScrapInstant, OffsetDateTime latestScrapKst
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
                            DIGEST_VERSION
                    )
            );
        }

        return new StageDigestResponse(
                new StageDigestResponse.ProjectDto(projectId, projectName),
                stage,
                null,
                new StageDigestResponse.Meta(
                        false,
                        false,
                        null,
                        latestScrapKst,
                        null,
                        DIGEST_VERSION
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

