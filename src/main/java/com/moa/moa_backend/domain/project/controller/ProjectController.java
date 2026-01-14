package com.moa.moa_backend.domain.project.controller;

import com.moa.moa_backend.domain.project.dto.ProjectDto;
import com.moa.moa_backend.domain.project.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import java.util.List;

@RestController
@RequiredArgsConstructor // 생성자 주입을 위한 Lombok 어노테이션
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService; // Service 계층의 의존성 주입

//    @GetMapping
//    public List<ProjectDto.ListItem> getProjectList(){
//        return projectService.getProjectList();
//    }

    @GetMapping
    public List<ProjectDto.ListItem> getProjectListByUserId(HttpServletRequest request){
        Long userId = (Long) request.getAttribute("userId");    //UserdFilter가 넣어줄 수 있게.
        return projectService.getProjectList(userId);
    }
}