package com.moa.moa_backend.domain.project.controller;

import com.moa.moa_backend.domain.project.dto.ProjectDto;
import com.moa.moa_backend.domain.project.service.ProjectService;
import com.moa.moa_backend.global.error.ApiException;
import com.moa.moa_backend.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor // 생성자 주입을 위한 Lombok 어노테이션
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService; // Service 계층의 의존성 주입


    @Operation(
            summary = "프로젝트 목록 조회",
            description = """
                    요청 사용자(X-User-Id)의 프로젝트 목록을 조회합니다.
                    최신 수정일(updatedAt) 기준 내림차순으로 정렬됩니다.
                    """,
            parameters = {
                    @Parameter(
                            name = "X-User-Id",
                            in = ParameterIn.HEADER,
                            required = true,
                            example = "1",
                            description = "요청 사용자 ID"
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "프로젝트 목록 조회 성공",
                            content = @Content(schema = @Schema(implementation = ProjectDto.ListResponse.class))
                    )
            }
    )
    // 프로젝트 목록
    @GetMapping
    public ProjectDto.ListResponse getProjectListByUserId(HttpServletRequest request){
        Long userId = requireUserId(request);
        return projectService.getProjectList(userId);
    }

    @Operation(
            summary = "프로젝트 개수 조회",
            description = "요청 사용자(X-User-Id)의 프로젝트 개수를 반환합니다.",
            parameters = {
                    @Parameter(
                            name = "X-User-Id",
                            in = ParameterIn.HEADER,
                            required = true,
                            example = "1",
                            description = "요청 사용자 ID"
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "프로젝트 개수 조회 성공",
                            content = @Content(schema = @Schema(implementation = ProjectDto.CountResponse.class))
                    )
            }
    )
    // 프로젝트 개수
    @GetMapping("/count")
    public ProjectDto.CountResponse getProjectCount(HttpServletRequest request) {
        Long userId = requireUserId(request);

        long count = projectService.getProjectCount(userId);

        return new ProjectDto.CountResponse(count);
    }


    @Operation(
            summary = "프로젝트 생성",
            description = """
                    요청 사용자(X-User-Id)의 프로젝트를 생성합니다.
                    생성 성공 시 201과 함께 Location 헤더(/api/projects/{projectId})가 반환됩니다.
                    """,
            parameters = {
                    @Parameter(
                            name = "X-User-Id",
                            in = ParameterIn.HEADER,
                            required = true,
                            example = "1",
                            description = "요청 사용자 ID"
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "프로젝트 생성 성공",
                            content = @Content(schema = @Schema(implementation = ProjectDto.CreateResponse.class))
                    )
            }
    )
    // 프로젝트 생성
    @PostMapping
    public ResponseEntity<ProjectDto.CreateResponse> createProject(
            HttpServletRequest request,
            @Valid @RequestBody ProjectDto.CreateRequest body
    ) {
        Long userId = requireUserId(request);

        ProjectDto.CreateResponse created = projectService.createProject(userId, body);

        // Location 헤더: /api/projects/{id}
        return ResponseEntity
                .created(URI.create("/api/projects/" + created.projectId()))
                .body(created);
    }

    @Operation(
            summary = "프로젝트 수정",
            description = """
                    요청 사용자(X-User-Id)가 소유한 프로젝트를 수정합니다.
                    name/description은 선택이며, 둘 다 누락되면 INVALID_REQUEST 처리합니다.
                    """,
            parameters = {
                    @Parameter(
                            name = "X-User-Id",
                            in = ParameterIn.HEADER,
                            required = true,
                            example = "1",
                            description = "요청 사용자 ID"
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "프로젝트 수정 성공",
                            content = @Content(schema = @Schema(implementation = ProjectDto.UpdateResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "프로젝트가 없거나 사용자 소유가 아님",
                            content = @Content
                    )
            }
    )
    // 프로젝트 수정
    @PatchMapping("/{projectId}")
    public ProjectDto.UpdateResponse updateProject(
            HttpServletRequest request,
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectDto.UpdateRequest body
    ) {
        Long userId = requireUserId(request);
        return projectService.updateProject(userId, projectId, body);
    }


    @Operation(
            summary = "프로젝트 삭제",
            description = """
                    요청 사용자(X-User-Id)가 소유한 프로젝트를 삭제합니다.
                    성공 시 204 No Content를 반환합니다.
                    """,
            parameters = {
                    @Parameter(
                            name = "X-User-Id",
                            in = ParameterIn.HEADER,
                            required = true,
                            example = "1",
                            description = "요청 사용자 ID"
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "프로젝트 삭제 성공"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "프로젝트가 없거나 사용자 소유가 아님",
                            content = @Content
                    )
            }
    )
    // 프로젝트 삭제
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(
            HttpServletRequest request,
            @PathVariable Long projectId
    ) {
        Long userId = requireUserId(request);

        projectService.deleteProject(userId, projectId);

        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(HttpServletRequest request) {
        Object obj = request.getAttribute("userId");
        if(obj instanceof Long userId){
            return userId;
        }
        throw new ApiException(ErrorCode.INVALID_REQUEST,"인증 정보가 없습니다.");
    }
}
