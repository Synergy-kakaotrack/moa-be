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

    @Transactional (readOnly = true)
    public List<ProjectDto> getProjectList(Long userId) {
        return projectRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(ProjectDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getProjectCount(Long userId) {
        return projectRepository.countByUserId(userId);
    }
}
