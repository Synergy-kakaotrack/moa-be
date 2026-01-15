package com.moa.moa_backend.domain.draft.service;

import com.moa.moa_backend.domain.draft.dto.*;
import com.moa.moa_backend.domain.draft.entity.Draft;
import com.moa.moa_backend.domain.draft.entity.DraftStage;
import com.moa.moa_backend.domain.draft.llm.LlmRecommendationPort;
import com.moa.moa_backend.domain.draft.repository.DraftRepository;
import com.moa.moa_backend.domain.project.repository.ProjectRepository;
import com.moa.moa_backend.domain.scrap.repository.ScrapRepository;
import com.moa.moa_backend.domain.scrap.service.ScrapService;
import com.moa.moa_backend.global.error.ApiException;
import com.moa.moa_backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Draft
 * 확장프로그램에서 스크랩한 내용을 임시 저장하고 LLM을 통해 프로젝트/단계/소제목을 추천받은 후
 * 사용자 확정 시 실제 Scrap으로 변환하는 역할
 */
@RequiredArgsConstructor
@Service
public class DraftService {

    private static final Duration DRAFT_TTL = Duration.ofHours(1);

    private final DraftRepository draftRepository;
    private final ProjectRepository projectRepository;
    private final ScrapRepository scrapRepository;
    private final LlmRecommendationPort llmRecommendationPort;
    private final ScrapService scrapService;


    /**
     * 드래프트 생성 및 LLM 추천
     * 1. 사용자의 프로젝트 목록 조회 (LLM이 선택할 수 있도록)
     * 2. 최근 스크랩 컨텍스트 조회 (이전 작업 패턴 파악)
     * 3. LLM에게 추천 요청 (프로젝트, 단계, 소제목)
     * 4. 추천 결과를 포함한 Draft 저장 (1시간 TTL)
     *
     * @param userId 사용자 ID
     * @param req 스크랩한 원본 내용 (텍스트, 코드, URL 등)
     * @return Draft ID와 추천 결과
     */
    @Transactional
    public DraftCreateResponse createDraft(Long userId, DraftCreateRequest req) {
        Instant now = Instant.now();
        Instant expiredAt = now.plus(DRAFT_TTL);

        // 1) 프로젝트 목록 조회 -> LLM 입력 projects
        var projectEntities = projectRepository.findAllByUserId(
                userId,
                Sort.by(Sort.Direction.DESC, "updatedAt")
        );
        List<DraftRecommendCommand.ProjectOption> projects = projectEntities.stream()
                .map(p -> new DraftRecommendCommand.ProjectOption(p.getId(), p.getName()))
                .toList();

        // 2) 최근 scrap 컨텍스트 조회 -> LLM 입력 recentContext
        DraftRecommendCommand.RecentContext recentContext = scrapRepository.findFirstByUserIdOrderByCapturedAtDesc(userId)
                .map(s -> new DraftRecommendCommand.RecentContext(
                        s.getProjectId(),
                        s.getStage(),
                        s.getCapturedAt()
                ))
                .orElse(null);

        // 3) fixedStages -> 작업단계 서버 고정 목록)
        List<String> fixedStages = DraftStage.FIXED_STAGES;


        // 4) LLM 추천 실행
        DraftRecommendCommand command = new DraftRecommendCommand(
                userId,
                req.contentPlain(),
                req.sourceCode(),
                req.sourceUrl(),
                projects,
                fixedStages,
                recentContext,
                now
        );

        DraftRecommendation rec = llmRecommendationPort.recommend(command);

        // 5) drafts 저장
        Draft saved = draftRepository.save(Draft.create(
                userId,
                req.contentPlain(),
                req.sourceCode(),
                req.sourceUrl(),
                rec.projectId(),
                rec.stage(),
                rec.subtitle(),
                rec.recMethod(),
                now,
                expiredAt
        ));

        return new DraftCreateResponse(
                saved.getId(),
                new DraftCreateResponse.Recommendation(
                        rec.projectId(),
                        rec.stage(),
                        rec.subtitle()
                ),
                rec.recMethod()
        );

    }

//TODO 아직 최종 저장하는걸 안만들어서 드래프트 만들고 조회하려고 일단 만들어두고, 나중에 필요없으면 주석처리할 예정
    /**
     * 최신 드래프트 조회
     *
     * @param userId 사용자 ID
     * @return 만료되지 않은 가장 최근 Draft 정보
     * @throws RuntimeException Draft가 없거나 모두 만료된 경우 404
     */
    @Transactional(readOnly = true)
    public DraftLatestResponse getLatestDraft(Long userId) {
        Instant now = Instant.now();

        Draft d = draftRepository.findFirstByUserIdAndExpiredAtAfterOrderByCreatedAtDesc(userId, now)
                .orElseThrow(); // TODO: ApiException(404)

        return new DraftLatestResponse(
                d.getId(),
                d.getRecProjectId(),
                d.getRecStage(),
                d.getRecSubtitle(),
                d.getExpiredAt()
        );
    }


    /**
     * 드래프트 커밋 (최종저장)
     * 1. Draft 존재 및 만료 여부 확인
     * 2. 사용자가 제출한 데이터 검증
     * 3. Scrap 엔티티 생성 및 저장
     * 4. Draft 삭제 (같은 트랜잭션)
     *
     * @param userId 사용자 ID
     * @param draftId 확정할 Draft ID
     * @param req 사용자가 최종 확정한 데이터 (프로젝트, 단계, 소제목 등)
     * @return 생성된 Scrap ID
     */
    @Transactional
    public DraftCommitResponse commit(Long userId, Long draftId, DraftCommitRequest req) {
        Instant now = Instant.now();

        // Draft 조회 (존재 여부 + 소유자 확인)
        // 소유자 불일치도 404로 처리
        Draft draft = draftRepository.findByIdAndUserId(draftId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.DRAFT_NOT_FOUND));


        // 만료 확인 - 410 Gone
        // 리소스가 존재했지만 더 이상 유효하지 않음
        if (draft.isExpired(now)) {
            throw new ApiException(ErrorCode.DRAFT_EXPIRED);
        }

        Long scrapId = scrapService.createFromDraftCommit(
                userId,
                req,
                draft.getRecMethod(),
                now
        );

        // draft 삭제 (같은 트랜잭션) -> Scrap 저장 성공 시에만 Draft 삭제됨
        draftRepository.delete(draft);

        return new DraftCommitResponse(scrapId);
    }

    /**
     * 드래프트 삭제
     *
     * @param userId 사용자 ID
     * @param draftId 삭제할 Draft ID
     */
    @Transactional
    public void deleteDraft(Long userId, Long draftId) {
        // 멱등: 없어도 204
        draftRepository.deleteByIdAndUserId(draftId, userId);

    }


    /**
     * 필수 문자열 필드 검증 및 정규화
     *
     * @param v 검증할 값
     * @param fieldName 필드명 (에러 메시지용)
     * @return trim된 문자열
     * @throws ApiException INVALID_REQUEST (400) -> null이거나 공백만 있는 경우
     */
    private static String normalizeRequired(String v, String fieldName) {
        if (v == null) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    fieldName + "은(는) 필수입니다."
            );
        }
        String t = v.trim();
        if (t.isEmpty()) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    fieldName + "은(는) 빈 문자열일 수 없습니다."
            );
        }
        return t;
    }
}
