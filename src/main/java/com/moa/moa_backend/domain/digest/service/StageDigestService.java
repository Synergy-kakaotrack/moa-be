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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

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

    private final StageDigestGeneratorPort digestGenerator; //Port

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

    @Transactional(readOnly = true)
    public StageDigestResponse getDigest(Long userId, Long projectId, String stage) {
        Project project = getOwnedProjectOrThrow(userId, projectId);

        //digest 조회
        Optional<StageDigest> digestOpt =
                stageDigestRepository.findByUserIdAndProjectIdAndStage(userId, projectId, stage);

        //최신 스크랩 캡처시간 조회
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
                            false, // 요약 없으면 outdated=false
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

    @Transactional
    public StageDigestResponse refresh(Long userId, Long projectId, String stage) {
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

        // LLM 실패 시 기존 유지 위해 미리 조회
        Optional<StageDigest> existingOpt =
                stageDigestRepository.findByUserIdAndProjectIdAndStage(userId, projectId, stage);

        List<ScrapForDigestView> scraps = scrapForDigestRepository.findRecentForDigest(
                userId, projectId, stage, PageRequest.of(0, INPUT_SCRAPS_LIMIT)
        );

        // raw_html 전처리/길이 제한 적용
        List<ScrapForDigestView> normalized = scraps.stream()
                .map(s -> new ScrapForDigestView(
                        s.scrapId(),
                        s.subtitle(),
                        s.memo(),
                        DigestInputNormalizer.normalizeRawHtml(s.rawHtml()),
                        s.capturedAt()
                ))
                .toList();

        final String markdown;
        try {
            markdown = digestGenerator.generateMarkdown(project.getName(), stage, normalized);
        } catch (Exception e) {

            log.error("[DIGEST] refresh failed. userId={}, projectId={}, stage={}, scraps={}",
                    userId, projectId, stage, normalized.size(), e);

            // 기존 digest 있으면 유지
            if (existingOpt.isPresent()) {
                StageDigest existing = existingOpt.get();
                String existingText = existing.getDigestText();
                boolean exists = (existingText != null && !existingText.isBlank());
                boolean outdated = exists && computeOutdated(existing.getSourceLastCapturedAt(), latestScrapInstant);

                return new StageDigestResponse(
                        new StageDigestResponse.ProjectDto(projectId, project.getName()),
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

            // 기존이 없으면 요약 없음으로 반환(200 유지)
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

        OffsetDateTime sourceLastCapturedAt = latestScrapKst;

        StageDigest digest = existingOpt
                .orElseGet(() -> StageDigest.create(userId, projectId, stage, null, sourceLastCapturedAt));

        digest.updateDigest(markdown, sourceLastCapturedAt);
        StageDigest saved = stageDigestRepository.save(digest);

        return new StageDigestResponse(
                new StageDigestResponse.ProjectDto(projectId, project.getName()),
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
                .orElseThrow(() -> new IllegalArgumentException("project not found"));

        if (!p.getUserId().equals(userId)) {
            throw new SecurityException("forbidden");
        }
        return p;
    }
}

