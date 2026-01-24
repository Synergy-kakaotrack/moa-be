package com.moa.moa_backend.domain.draft.service;

import com.moa.moa_backend.domain.draft.dto.*;
import com.moa.moa_backend.domain.draft.entity.RecMethod;
import com.moa.moa_backend.domain.draft.llm.LlmRecommendationPort;
import com.moa.moa_backend.domain.draft.repository.DraftRepository;
import com.moa.moa_backend.domain.project.entity.Project;
import com.moa.moa_backend.domain.project.repository.ProjectRepository;
import com.moa.moa_backend.domain.scrap.repository.ScrapRepository;
import com.moa.moa_backend.global.error.ApiException;
import com.moa.moa_backend.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;


import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)   // 생성자 주입 자동화
class DraftCommitServiceTest {

    private final DraftService draftService;
    private final DraftRepository draftRepository;
    private final ScrapRepository scrapRepository;
    private final ProjectRepository projectRepository;
    private final EntityManager em;

    DraftCommitServiceTest(
            DraftService draftService,
            DraftRepository draftRepository,
            ScrapRepository scrapRepository,
            ProjectRepository projectRepository,
            EntityManager em
    ) {
        this.draftService = draftService;
        this.draftRepository = draftRepository;
        this.scrapRepository = scrapRepository;
        this.projectRepository = projectRepository;
        this.em = em;
    }

    @MockitoBean // 가짜 객체로 교체 (LLM 호출을 실제로 하지 않기 위해)
    LlmRecommendationPort llmRecommendationPort;

    // 롤백 테스트에서 delete에서 예외를 강제로 터뜨리기 위해 스파이
    @MockitoSpyBean // 실제 객체를 감싸서 일부 메서드만 가짜로 동작시킬 수 있음
    DraftRepository spyDraftRepository;

    private Long userId;
    private Long projectId;
    private Long draftId;

    @BeforeEach
    void setUp() {
        // 1. DB 초기화 (테스트 격리)
        scrapRepository.deleteAll();
        draftRepository.deleteAll();
        projectRepository.deleteAll();

        //2. 테스트 데이터 준비
        userId = 1L;
        Project p = Project.create(userId, "MOA", "프로젝트 설명");
        projectId = projectRepository.save(p).getId();

        // 3. LLM 응답을 고정값으로 설정 (실제 API 호출 방지)
        when(llmRecommendationPort.recommend(any()))
                .thenReturn(new DraftRecommendation(
                        projectId,
                        "설계",
                        "API 설계 초안",
                        RecMethod.LLM
                ));

        // 4. Draft 생성 (테스트할 대상)
        DraftCreateRequest createReq = new DraftCreateRequest(
                "임시 원문 contentPlain",
                "CHATGPT",
                "https://example.com"
        );
        DraftCreateResponse created = draftService.createDraft(userId, createReq);
        draftId = created.draftId();
    }

    private DraftCommitRequest commitReq() {
        return new DraftCommitRequest(
                projectId,              // projectId
                "설계",                 // stage
                "소제목",               // subtitle
                null,                   // memo
                "<p>Hello</p>",         // rawHtml (필수)
                "CHATGPT",              // aiSource
                "https://example.com",  // aiSourceUrl
                false, false, false
        );
    }


    //1) 성공 케이스
//    @Test
//    @Transactional
//    void commit_success_creates_scrap_and_deletes_draft() {
//        // when
//        DraftCommitResponse resp = draftService.commit(userId, draftId, commitReq());
//        em.flush();
//        em.clear();
//
//        // then: draft 삭제
//        assertThat(draftRepository.findByIdAndUserId(draftId, userId)).isEmpty();
//
//        // then: scrap 생성(최소한 id로 존재 확인)
//        assertThat(scrapRepository.findById(resp.scrapId())).isPresent();
//    }

    //2) 중복 커밋 방지(삭제 기반이면 2번째는 NOT_FOUND가 자연스러움)
//    @Test
//    @Transactional
//    void commit_twice_should_fail_second_time() {
//        draftService.commit(userId, draftId, commitReq());
//        em.flush();
//        em.clear();
//
//        assertThatThrownBy(() -> draftService.commit(userId, draftId, commitReq()))
//                .isInstanceOf(ApiException.class)
//                .satisfies(ex -> {
//                    ApiException ae = (ApiException) ex;
//                    assertThat(ae.getErrorCode()).isEqualTo(ErrorCode.DRAFT_NOT_FOUND);
//                });
//    }

    /**
     * 3) 트랜잭션 원자성(롤백) 테스트:
     * scrap 저장 후 draft 삭제에서 예외 터뜨리면, 최종적으로 scrap도 없어야 하고 draft도 그대로 남아야 함.
     *
     * 1.삭제 단계에서 강제로 예외 만들기
     * - 이 시점에 예외가 터져야 커밋이 실패한다.
     *
     * 2. commit 호출이 실제로 예외를 내는지 확인
     * - “어디서 터졌는지”를 메시지로 확인해서
     * - 다른 이유로 실패한 걸 통과로 착각하지 않게 한다.
     *
     * 3. DB 상태 검증
     * - Draft가 남아있다: 삭제가 롤백되었다
     * - Scrap이 0이다: 저장도 롤백되었다
     */
    @Test
    void commit_should_rollback_when_draft_delete_fails() {
        // given: deleteByIdAndUserId 호출 시점에 강제로 예외 발생
        doThrow(new RuntimeException("forced error"))
                .when(spyDraftRepository)
                .deleteByIdAndUserId(draftId, userId);


        // when: commit 수행 -> 예외가 터져야 함
        assertThatThrownBy(() -> draftService.commit(userId, draftId, commitReq()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("forced error");

        // then: 서비스 트랜잭션이 종료되며 롤백이 확정된 상태
        assertThat(draftRepository.findByIdAndUserId(draftId, userId)).isPresent();
        assertThat(scrapRepository.count()).isZero();
    }

}
