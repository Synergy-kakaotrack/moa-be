package com.moa.moa_backend.domain.project.controller;

import com.moa.moa_backend.domain.project.dto.ProjectDto;
import com.moa.moa_backend.domain.project.service.ProjectService;
import com.moa.moa_backend.global.error.ApiException;
import com.moa.moa_backend.global.error.ErrorCode;
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

    // 프로젝트 목록
    @GetMapping
    public ProjectDto.ListResponse getProjectListByUserId(HttpServletRequest request){
        Long userId = (Long) request.getAttribute("userId");    //UserdFilter가 넣어줄 수 있게.
        return projectService.getProjectList(userId);
    }

    // 프로젝트 개수
    @GetMapping("/count")
    public ProjectDto.CountResponse getProjectCount(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        long count = projectService.getProjectCount(userId);

        return new ProjectDto.CountResponse(count);
    }


    // 프로젝트 생성
    @PostMapping
    public ResponseEntity<ProjectDto.CreateResponse> createProject(
            HttpServletRequest request,
            @Valid @RequestBody ProjectDto.CreateRequest body
    ) {
        Long userId = (Long) request.getAttribute("userId");

        ProjectDto.CreateResponse created = projectService.createProject(userId, body);

        // Location 헤더: /api/projects/{id}
        return ResponseEntity
                .created(URI.create("/api/projects/" + created.projectId()))
                .body(created);
    }

    // 프로젝트 수정
    @PatchMapping("/{projectId}")
    public ProjectDto.UpdateResponse updateProject(
            HttpServletRequest request,
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectDto.UpdateRequest body
    ) {
        Long userId = (Long) request.getAttribute("userId");
        return projectService.updateProject(userId, projectId, body);
    }

    // 프로젝트 삭제
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(
            HttpServletRequest request,
            @PathVariable Long projectId
    ) {
        Long userId = (Long) request.getAttribute("userId");

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