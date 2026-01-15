package com.moa.moa_backend.domain.draft.controller;

import com.moa.moa_backend.domain.draft.dto.*;
import com.moa.moa_backend.domain.draft.service.DraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;


/**
 * 드래프트 생성 + LLM 추천
 * 사용 시점: 확장프로그램에서 사용자가 텍스트를 드래그/선택하고 스크랩완료 버튼을 클릭했을 때
 *
 * 요청 데이터:
 * - contentPlain: 스크랩한 텍스트
 * - sourceCode: 코드 블록 (선택)
 * - sourceUrl: 출처 URL
 *
 * 응답 데이터:
 * - draftId: 생성된 Draft ID (나중에 커밋/삭제 시 사용)
 * - recommendation: LLM이 추천한 프로젝트, 단계, 소제목
 * - recMethod: 추천 방식 (RULE_BASED, LLM 등)
 *
 * HTTP 상태:
 * - 201 Created: 성공적으로 Draft 생성 및 추천 완료
 * - Location 헤더에 생성된 리소스 URI 포함
 */
@Tag(
        name = "Draft API",
        description = "확장프로그램에서 생성되는 임시 드래프트 및 추천 관리 API"
)
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/drafts")
public class DraftController {

    private final DraftService draftService;

    @Operation(
            summary = "드래프트 생성",
            description = """
                    확장프로그램에서 드래그/스크랩한 텍스트를 기반으로
                    프로젝트 / 작업단계 / 소제목 추천을 생성하고
                    TTL 1시간짜리 draft로 저장합니다.
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "드래프트 생성 성공",
                            content = @Content(schema = @Schema(implementation = DraftCreateResponse.class))
                    )
            }
    )
    @PostMapping
    public ResponseEntity<DraftCreateResponse> create(
            @Parameter(
                    description = "요청 사용자 ID",
                    required = true,
                    example = "1"
            )
            @RequestHeader("X-User-Id") Long userId,

            @RequestBody DraftCreateRequest request
    ) {
        DraftCreateResponse res = draftService.createDraft(userId, request);
        return ResponseEntity
                .created(URI.create("/api/drafts/" + res.draftId()))
                .body(res);
    }

    /**
     * 최신 드래프트 조회
     *
     * 응답 데이터:
     * - draftId: Draft ID
     * - recProjectId: 추천된 프로젝트 ID
     * - recStage: 추천된 작업 단계
     * - recSubtitle: 추천된 소제목
     * - expiredAt: 만료 시각 (프론트에서 타이머 표시 가능)
     *
     * HTTP 상태:
     * - 200 OK: 유효한 Draft 존재
     * - 404 Not Found: 만료되지 않은 Draft 없음
     */
    @Operation(
            summary = "최신 드래프트 조회",
            description = "만료되지 않은 가장 최근 draft 1건을 조회합니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "조회 성공",
                            content = @Content(schema = @Schema(implementation = DraftLatestResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "존재하는 드래프트 없음"
                    )
            }
    )
    @GetMapping("/latest")
    public DraftLatestResponse latest(
            @Parameter(
                    description = "요청 사용자 ID",
                    required = true,
                    example = "1"
            )
            @RequestHeader("X-User-Id") Long userId
    ) {
        return draftService.getLatestDraft(userId);
    }


    /**
     * 드래프트 커밋 (확정하여 실제 Scrap으로 저장)
     *
     * 사용 시점:
     * - 사용자가 추천을 확인/수정하고 "저장" 버튼 클릭 시
     *
     * 요청 데이터:
     * - projectId: 최종 선택한 프로젝트 (LLM 추천 또는 사용자 변경)
     * - stage: 최종 선택한 단계
     * - subtitle: 최종 확정한 소제목
     * - memo: 사용자 메모 (선택)
     * - rawHtmlGzipBase64: 원본 HTML (gzip + base64)
     * - aiSource: AI 출처 정보
     * - aiSourceUrl: AI 출처 URL
     * - userRecProject: 사용자가 프로젝트를 변경했는가?
     * - userRecStage: 사용자가 단계를 변경했는가?
     * - userRecSubtitle: 사용자가 소제목을 변경했는가?
     *
     * 응답 데이터:
     * - scrapId: 생성된 Scrap ID
     *
     * HTTP 상태:
     * - 201 Created: Scrap 생성 성공, Draft 삭제됨
     * - 404 Not Found: Draft가 없거나 다른 사용자 소유
     * - 410 Gone: Draft 만료됨 (TODO: 정책 결정 필요)
     *
     * 트랜잭션:
     * - Scrap 저장과 Draft 삭제가 원자적으로 처리됨
     * - 하나라도 실패하면 전체 롤백
     */
    @Operation(
            summary = "드래프트 커밋 (스크랩 생성)",
            description = """
                    추천 결과를 사용자가 확정하여 scraps 테이블에 저장합니다.
                    성공 시 draft는 같은 트랜잭션에서 삭제됩니다.
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "스크랩 생성 성공",
                            content = @Content(schema = @Schema(implementation = DraftCommitResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "드래프트 없음 또는 소유자 불일치"
                    )
            }
    )
    @PostMapping("/{draftId}/commit")
    public ResponseEntity<DraftCommitResponse> commit(
            @Parameter(
                    description = "요청 사용자 ID",
                    required = true,
                    example = "1"
            )
            @RequestHeader("X-User-Id") Long userId,

            @Parameter(
                    description = "커밋할 드래프트 ID",
                    required = true,
                    example = "100"
            )
            @PathVariable Long draftId,

            @RequestBody DraftCommitRequest request
    ) {
        DraftCommitResponse res = draftService.commit(userId, draftId, request);
        return ResponseEntity.status(201).body(res);
    }

    /**
     * 드래프트 삭제

     * HTTP 상태:
     * - 204 No Content: 삭제 성공
     *
     * - 이미 삭제되었거나 존재하지 않아도 204 반환
     * - 여러 번 호출해도 안전함
     * - RESTful 설계 원칙 준수
     */
    @Operation(
            summary = "드래프트 삭제",
            description = """
                    사용자가 추천을 버리는 경우 드래프트를 삭제합니다.
                    이미 삭제되었거나 존재하지 않아도 204를 반환하여 멱등성을 보장합니다. 
                    """,
            responses = {
                    @ApiResponse(responseCode = "204", description = "삭제 성공")
            }
    )
    @DeleteMapping("/{draftId}")
    public ResponseEntity<Void> delete(
            @Parameter(
                    description = "요청 사용자 ID",
                    required = true,
                    example = "1"
            )
            @RequestHeader("X-User-Id") Long userId,

            @Parameter(
                    description = "삭제할 드래프트 ID",
                    required = true,
                    example = "100"
            )
            @PathVariable Long draftId
    ) {
        draftService.deleteDraft(userId, draftId);
        return ResponseEntity.noContent().build();
    }
}

