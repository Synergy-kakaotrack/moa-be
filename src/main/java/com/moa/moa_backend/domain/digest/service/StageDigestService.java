package com.moa.moa_backend.domain.digest.service;

import com.moa.moa_backend.domain.digest.dto.StageDigestResponse;
import com.moa.moa_backend.domain.digest.entity.StageDigest;
import com.moa.moa_backend.domain.digest.llm.StageDigestGeneratorPort;
import com.moa.moa_backend.domain.digest.repository.StageDigestRepository;
import com.moa.moa_backend.domain.project.entity.Project;
import com.moa.moa_backend.domain.project.repository.ProjectRepository;
import com.moa.moa_backend.domain.scrap.repository.ScrapDigestQueryRepository;
import com.moa.moa_backend.domain.scrap.repository.ScrapForDigestRepository;
import com.moa.moa_backend.domain.scrap.repository.ScrapForDigestView;
import com.moa.moa_backend.global.error.ApiException;
import com.moa.moa_backend.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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

    //유니크 단위로 갱신 중 상태 관리
    // NOTE: inFlight는 단일 인스턴스에서만 유효. 멀티 인스턴스면 분산락 필요
    private final ConcurrentHashMap<String, Boolean> inFlight = new ConcurrentHashMap<>();

    private String key(Long userId, Long projectId, String stage) {
        return userId + ":" + projectId + ":" + stage;
    }


    public StageDigestService(
            ProjectRepository projectRepository,
            StageDigestRepository stageDigestRepository,
            ScrapDigestQueryRepository scrapDigestQueryRepository,
            ScrapForDigestRepository scrapForDigestRepository,
            StageDigestGeneratorPort digestGenerator
    ) {
        this.projectRepository = projectRepository;
        this.stageDigestRepository = stageDigestRepository;
        this.scrapDigestQueryRepository = scrapDigestQueryRepository;
        this.scrapForDigestRepository = scrapForDigestRepository;
        this.digestGenerator = digestGenerator;
    }

    // =========================
    // 조회 API (LLM 호출 없음)
    // =========================
    @Transactional(readOnly = true)
    public StageDigestResponse getDigest(Long userId, Long projectId, String stage) {
        Project project = getOwnedProjectOrThrow(userId, projectId);

        //유니크 기준으로 digest 1건 조회
        Optional<StageDigest> digestOpt =
                stageDigestRepository.findByUserIdAndProjectIdAndStage(userId, projectId, stage);

        //해당 단계의 최신 스크랩 캡처 시각 조회
        Instant latestScrapInstant =
                scrapDigestQueryRepository.findLatestCapturedAt(userId, projectId, stage);

        OffsetDateTime latestScrapKst = toKst(latestScrapInstant);

        //digest 없으면 exists = false 응답
        if (digestOpt.isEmpty()) {
            return new StageDigestResponse(
                    new StageDigestResponse.ProjectDto(projectId, project.getName()),
                    stage,
                    null,
                    new StageDigestResponse.Meta(
                            false,
                            false, // 요약 자체가 없으므로 outdated 개념 없음
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
        //최신 스크랩이 digest생성 기준 시점보다 이후면 outdated
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
    // =========================
    @Transactional
    public StageDigestResponse refresh(Long userId, Long projectId, String stage) {

        String lockKey = key(userId, projectId, stage);

        // 이미 처리 중이면 409 반환
        if (inFlight.putIfAbsent(lockKey, Boolean.TRUE) != null) {
            throw new ApiException(ErrorCode.DIGEST_REFRESH_IN_PROGRESS);
        }
        try {
            Project project = getOwnedProjectOrThrow(userId, projectId);

            //최신 스크랩 기준 시각 (digest 최신성 판단 기준)
            Instant latestScrapInstant =
                    scrapDigestQueryRepository.findLatestCapturedAt(userId, projectId, stage);

            //스크랩 자체가 없으면 요약 대상이 없음
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

            //llm 입력으로 사용할 최근 스크랩 조회
            List<ScrapForDigestView> scraps = scrapForDigestRepository.findRecentForDigest(
                    userId, projectId, stage, PageRequest.of(0, INPUT_SCRAPS_LIMIT)
            );

            //rawHtml 정규화
            List<ScrapForDigestView> normalized = scraps.stream()
                    .map(s -> new ScrapForDigestView(
                            s.scrapId(),
                            s.subtitle(),
                            s.memo(),
                            DigestInputNormalizer.normalizeRawHtml(s.rawHtml()),
                            s.capturedAt()
                    ))
                    .toList();

            //LLM 호출 전에 "의미 있는 입력"이 있는지 검사
            boolean hasAnyInput = normalized.stream().anyMatch(s ->
                    (s.rawHtml() != null && !s.rawHtml().isBlank()) ||
                            (s.subtitle() != null && !s.subtitle().isBlank()) ||
                            (s.memo() != null && !s.memo().isBlank())
            );

            if (!hasAnyInput) {
                log.debug("[DIGEST] refresh skipped (no meaningful scraps). userId={}, projectId={}, stage={}",userId, projectId, stage);
                // LLM에 넣을 게 없으면 기존 digest 유지(없으면 null)
                return fallbackExistingDigest(
                        userId, projectId, project.getName(), stage,
                        latestScrapInstant, latestScrapKst
                );
            }

            // -------------------------
            // LLM 호출 (실패 가능 영역)
            // -------------------------
            final String markdown;
            try {
                markdown = digestGenerator.generateMarkdown(project.getName(), stage, normalized);
            } catch (Exception e) {
                // LLM 실패 시에도 API는 200 유지
                // - 기존 digest가 있으면 그대로 반환
                // - 없으면 digest=null 응답
                log.error("[DIGEST] refresh failed. userId={}, projectId={}, stage={}, scraps={}",
                        userId, projectId, stage, normalized.size(), e);

                return fallbackExistingDigest(userId, projectId, project.getName(), stage, latestScrapInstant, latestScrapKst);
            }

            OffsetDateTime sourceLastCapturedAt = latestScrapKst;

            // ======================================================
            // 동시성 대응 Upsert 로직 (JPA 재시도 방식)
            // ======================================================

            // 1) 먼저 조회해서 있으면 update
            Optional<StageDigest> existingOpt =
                    stageDigestRepository.findByUserIdAndProjectIdAndStage(userId, projectId, stage);

            if (existingOpt.isPresent()) {
                StageDigest existing = existingOpt.get();
                existing.updateDigest(markdown, sourceLastCapturedAt);
                StageDigest saved = stageDigestRepository.save(existing);

                return successResponse(projectId, project.getName(), stage, markdown, saved, latestScrapKst);
            }

            // 2) 없으면 insert 시도
            try {
                StageDigest created = StageDigest.create(userId, projectId, stage, null, sourceLastCapturedAt);
                created.updateDigest(markdown, sourceLastCapturedAt);
                StageDigest saved = stageDigestRepository.saveAndFlush(created);

                return successResponse(projectId, project.getName(), stage, markdown, saved, latestScrapKst);

            } catch (DataIntegrityViolationException e) {
                // 3) 동시 요청으로 인해 조회 시점엔 없었지만, Insert 순간 다른 트랜잭션이 먼저 삽입한 경우
                log.warn("[DIGEST] upsert race detected. retry update. userId={}, projectId={}, stage={}",
                        userId, projectId, stage, e);

                // 다시 조회해서 update로 전환
                StageDigest nowExisting = stageDigestRepository
                        .findByUserIdAndProjectIdAndStage(userId, projectId, stage)
                        .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR));

                nowExisting.updateDigest(markdown, sourceLastCapturedAt);
                StageDigest saved = stageDigestRepository.save(nowExisting);

                return successResponse(projectId, project.getName(), stage, markdown, saved, latestScrapKst);
            }
        } finally {
            // 성공/실패/예외 상관없이 반드시 해제
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

    // LLM 실패 시 기존 digest 유지용 fallback
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

    // 최신 스크랩 캡처시간이 소스 기준시간보다 이후면 outdated
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

