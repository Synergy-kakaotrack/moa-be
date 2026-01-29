package com.moa.moa_backend.domain.digest.controller;

import com.moa.moa_backend.domain.digest.dto.StageDigestResponse;
import com.moa.moa_backend.domain.digest.service.StageDigestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * 작업단계 요약 API
 * - refresh는 body 없이 호출(버튼 트리거)
 */
@Tag(
        name = "Stage Digest API",
        description = "프로젝트-작업 단계별 요약 조회/갱신 API"
)
@RestController
@RequestMapping("/api/projects/{projectId}/stages/{stage}")
public class StageDigestController {

    private final StageDigestService service;

    public StageDigestController(StageDigestService service) {
        this.service = service;
    }

    @Operation(
            summary = "작업단계 요약 조회",
            description = """
            프로젝트 + 작업단계(stage) 단위의 요약을 조회합니다.
            - GET에서는 LLM을 호출하지 않습니다.
            - digest가 없더라도 200을 반환하며, meta.exists로 구분합니다.
            - meta.outdated=true 는 최신 스크랩 기준으로 요약이 갱신 필요함을 의미합니다.
            - meta.refresh 는 최근 refresh 시도 결과가 있을 경우에만 포함될 수 있습니다.
            """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "조회 성공",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = StageDigestResponse.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "요청값 오류"),
                    @ApiResponse(responseCode = "404", description = "프로젝트 없음/권한 없음")
            }
    )

    @GetMapping("/digest")
    public StageDigestResponse getDigest(
            @Parameter(description = "요청 사용자 ID (X-User-Id 헤더)", required = true, example = "1")
            @RequestHeader("X-User-Id") Long userId,

            @Parameter(description = "프로젝트 ID", required = true, example = "15")
            @PathVariable Long projectId,

            @Parameter(description = "작업단계 (예: 설계)", required = true, example = "설계")
            @PathVariable String stage
    ) {
        return service.getDigest(userId, projectId, stage);
    }

    @Operation(
            summary = "작업단계 요약 생성/갱신 (버튼 트리거)",
            description = """
            요약을 새로 생성/갱신합니다.
            - 최근 스크랩들을 입력으로 LLM을 호출할 수 있습니다.
            - LLM 실패 시에도 기본적으로 200을 반환하며, 기존 요약이 있으면 유지합니다.
            - 성공/실패/스킵 여부는 HTTP status가 아니라 meta.refresh.status 로 판단합니다.
            """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "갱신 시도 결과 반환 (성공/스킵/실패 포함)",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = StageDigestResponse.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "요청값 오류"),
                    @ApiResponse(responseCode = "404", description = "프로젝트 없음/권한 없음"),
                    @ApiResponse(responseCode = "409", description = "이미 해당 작업단계 요약 갱신이 진행 중")
            }
    )
    @PostMapping("/digest:refresh")
    public StageDigestResponse refresh(
            @Parameter(description = "요청 사용자 ID (X-User-Id 헤더)", required = true, example = "1")
            @RequestHeader("X-User-Id") Long userId,

            @Parameter(description = "프로젝트 ID", required = true, example = "15")
            @PathVariable Long projectId,

            @Parameter(description = "작업단계 (예: 설계)", required = true, example = "설계")
            @PathVariable String stage
    ) {
        return service.refresh(userId, projectId, stage);
    }
}

