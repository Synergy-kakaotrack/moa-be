package com.moa.moa_backend.domain.project.service;

import com.moa.moa_backend.domain.project.dto.ProjectDto;
import com.moa.moa_backend.domain.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

//    @Transactional (readOnly = true)
//    public List<ProjectDto.ListItem> getProjectList() {
//        // 최신 수정 순(원하면 createdAt, id 등으로 변경)
//        var sort = Sort.by(Sort.Direction.DESC, "updatedAt");
//
//        return projectRepository.findAll(sort)
//                .stream()
//                .map(ProjectDto.ListItem::from)
//                .toList();
//    }

    @Transactional(readOnly = true)
    public List<ProjectDto.ListItem> getProjectList(Long userId) {
        var sort = Sort.by(Sort.Direction.DESC, "updatedAt");

        return projectRepository.findAllByUserId(userId, sort)
                .stream()
                .map(ProjectDto.ListItem::from)
                .toList();
    }

}