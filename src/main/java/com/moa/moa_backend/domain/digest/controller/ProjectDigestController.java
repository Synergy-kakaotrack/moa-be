package com.moa.moa_backend.domain.digest.controller;

import com.moa.moa_backend.domain.digest.dto.ProjectDigestResponse;
import com.moa.moa_backend.domain.digest.service.ProjectDigestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}")
@Tag(name = "ProjectDigest", description = "프로젝트 전체 요약 API")
public class ProjectDigestController {

    private final ProjectDigestService projectDigestService;

    public record RefreshRequest(
            @Schema(
                    description = "커스텀 요약 프롬프트. 비어있거나 null이면 DEFAULT 요약을 생성/갱신한다.",
                    example = "면접용으로 프로젝트를 핵심 성과 중심으로 요약해줘"
            )
            String prompt
    ) {}

    @GetMapping("/digest")
    @Operation(
            summary = "프로젝트 요약 조회",
            description = "프로젝트에 저장된 요약(마크다운)을 조회한다. 요약이 없으면 digest=null, meta.exists=false로 반환한다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = ProjectDigestResponse.class))
    )
    public ProjectDigestResponse get(
            @Parameter(description = "요청 사용자 ID", required = true, example = "1")
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "프로젝트 ID", required = true, example = "10")
            @PathVariable Long projectId
    ) {
        return projectDigestService.getDigest(userId, projectId);
    }

    @PostMapping("/digest:refresh")
    @Operation(
            summary = "프로젝트 요약 생성/갱신",
            description = """
                    - body.prompt가 비어있거나 null이면 DEFAULT 요약을 갱신한다.
                    - body.prompt가 있으면 CUSTOM 요약을 생성/갱신한다(프로젝트당 1개).
                    - DEFAULT는 최신이면 재생성하지 않고, meta.refresh.status=SKIPPED 로 내려준다.
                    """
    )
    @ApiResponse(
            responseCode = "200",
            description = "갱신 성공(또는 스킵/실패 시에도 200 + meta.refresh로 상태 노출 정책)",
            content = @Content(schema = @Schema(implementation = ProjectDigestResponse.class))
    )
    public ProjectDigestResponse refresh(
            @Parameter(description = "요청 사용자 ID", required = true, example = "1")
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "프로젝트 ID", required = true, example = "10")
            @PathVariable Long projectId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "커스텀 프롬프트(optional). 없거나 빈 값이면 DEFAULT 동작.",
                    required = false,
                    content = @Content(schema = @Schema(implementation = RefreshRequest.class))
            )
            @org.springframework.web.bind.annotation.RequestBody(required = false) RefreshRequest req
    ) {
        String prompt = (req == null) ? null : req.prompt();
        return projectDigestService.refresh(userId, projectId, prompt);
    }
}

