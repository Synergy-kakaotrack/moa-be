package com.moa.moa_backend.domain.project.controller;

import com.moa.moa_backend.domain.project.dto.ProjectDto;
import com.moa.moa_backend.domain.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequiredArgsConstructor // 생성자 주입을 위한 Lombok 어노테이션
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService; // Service 계층의 의존성 주입

    @GetMapping
    public List<ProjectDto> getProjectList(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return projectService.getProjectList(userId);
    }

    @GetMapping("/count")
    public ProjectDto getProjectCount(
            @RequestHeader("X-User-Id") Long userId
    ) {
        long count = projectService.getProjectCount(userId);
        return new ProjectDto(count);
    }
}
