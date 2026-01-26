package com.moa.moa_backend.domain.digest.service;

import com.moa.moa_backend.domain.digest.dto.StageDigestDto;
import com.moa.moa_backend.domain.digest.dto.StageDigestResponse;
import com.moa.moa_backend.domain.digest.entity.StageDigest;
import com.moa.moa_backend.domain.digest.repository.StageDigestRepository;
import com.moa.moa_backend.domain.project.entity.Project;
import com.moa.moa_backend.domain.project.repository.ProjectRepository;
import com.moa.moa_backend.domain.scrap.repository.ScrapDigestQueryRepository;
import com.moa.moa_backend.domain.scrap.repository.ScrapForDigestRepository;
import com.moa.moa_backend.domain.scrap.repository.ScrapForDigestView;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
public class StageDigestService {

    private static final int INPUT_SCRAPS_LIMIT = 20;
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final ProjectRepository projectRepository;
    private final StageDigestRepository stageDigestRepository;
    private final ScrapDigestQueryRepository scrapDigestQueryRepository;
    private final ScrapForDigestRepository scrapForDigestRepository;

    private final StageDigestLlmClient llmClient;
    private final DigestJsonCodec codec;

    public StageDigestService(
            ProjectRepository projectRepository,
            StageDigestRepository stageDigestRepository,
            ScrapDigestQueryRepository scrapDigestQueryRepository,
            ScrapForDigestRepository scrapForDigestRepository,
            StageDigestLlmClient llmClient,
            DigestJsonCodec codec
    ) {
        this.projectRepository = projectRepository;
        this.stageDigestRepository = stageDigestRepository;
        this.scrapDigestQueryRepository = scrapDigestQueryRepository;
        this.scrapForDigestRepository = scrapForDigestRepository;
        this.llmClient = llmClient;
        this.codec = codec;
    }

    /**
     * 단계 요약 조회
     * - LLM 호출 없음
     * - exists/outdated만 내려줘서 프론트가 갱신 버튼 UX 구성 가능
     */
    @Transactional(readOnly = true)
    public StageDigestResponse getDigest(Long userId, Long projectId, String stage) {
        Project project = getOwnedProjectOrThrow(userId, projectId);

        Optional<StageDigest> digestOpt =
                stageDigestRepository.findByUserIdAndProjectIdAndStage(userId, projectId, stage);

        // 최신 스크랩 시각(절대시간: Instant)
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
                            true,   // digest 없으면 갱신 유도
                            null,
                            latestScrapKst,
                            null
                    )
            );
        }

        StageDigest digest = digestOpt.get();
        StageDigestDto dto = codec.fromJson(digest.getDigestJson());

        boolean outdated = computeOutdated(digest.getSourceLastCapturedAt(), latestScrapInstant);

        return new StageDigestResponse(
                new StageDigestResponse.ProjectDto(projectId, project.getName()),
                stage,
                dto,
                new StageDigestResponse.Meta(
                        true,
                        outdated,
                        digest.getSourceLastCapturedAt(),
                        latestScrapKst,
                        digest.getUpdatedAt()
                )
        );
    }

    /**
     * 단계 요약 생성/갱신(버튼 트리거)
     * - 최근 20개만 입력으로 요약 생성
     * - LLM 실패 시 기존 digest 유지(트랜잭션 롤백)
     */
    @Transactional
    public StageDigestResponse refresh(Long userId, Long projectId, String stage) {
        Project project = getOwnedProjectOrThrow(userId, projectId);

        Instant latestScrapInstant =
                scrapDigestQueryRepository.findLatestCapturedAt(userId, projectId, stage);

        // 스크랩이 없으면 요약 생성 불가
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
                            null
                    )
            );
        }

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
                        s.capturedAt() // Instant 그대로 유지!
                ))
                .toList();

        StageDigestDto generated = llmClient.generate(project.getName(), stage, normalized);
        codec.validate(generated);

        String json = codec.toJson(generated);

        // 요약이 반영한 최신 스크랩 시각(응답/저장용: KST OffsetDateTime)
        OffsetDateTime sourceLastCapturedAt = toKst(latestScrapInstant);

        // upsert (find -> update/save)
        StageDigest digest = stageDigestRepository
                .findByUserIdAndProjectIdAndStage(userId, projectId, stage)
                .orElseGet(() -> StageDigest.create(userId, projectId, stage, json, sourceLastCapturedAt));

        digest.updateDigest(json, sourceLastCapturedAt);

        StageDigest saved = stageDigestRepository.save(digest);

        return new StageDigestResponse(
                new StageDigestResponse.ProjectDto(projectId, project.getName()),
                stage,
                generated,
                new StageDigestResponse.Meta(
                        true,
                        false,
                        saved.getSourceLastCapturedAt(),
                        toKst(latestScrapInstant),
                        saved.getUpdatedAt()
                )
        );
    }

    /**
     * outdated 판단:
     * - latestScrapInstant > sourceLastCapturedAt 이면 true
     */
    private boolean computeOutdated(OffsetDateTime sourceLastCapturedAt, Instant latestScrapInstant) {
        if (latestScrapInstant == null) return false;     // 스크랩 없음
        if (sourceLastCapturedAt == null) return true;    // 기준 없음

        return latestScrapInstant.isAfter(sourceLastCapturedAt.toInstant());
    }

    private OffsetDateTime toKst(Instant instant) {
        if (instant == null) return null;
        return instant.atOffset(ZoneOffset.UTC).withOffsetSameInstant(KST);
    }

    /**
     * 권한 검증: 본인 프로젝트만 접근 가능
     */
    private Project getOwnedProjectOrThrow(Long userId, Long projectId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("project not found"));

        if (!p.getUserId().equals(userId)) {
            throw new SecurityException("forbidden");
        }
        return p;
    }
}

