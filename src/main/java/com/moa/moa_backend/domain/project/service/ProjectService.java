package com.moa.moa_backend.domain.project.service;

import com.moa.moa_backend.domain.project.dto.ProjectDto;
import com.moa.moa_backend.domain.project.entity.Project;
import com.moa.moa_backend.domain.project.repository.ProjectRepository;
import com.moa.moa_backend.domain.user.repository.UserRepository;
import com.moa.moa_backend.global.error.ApiException;
import com.moa.moa_backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    // 프로젝트 목록
    @Transactional(readOnly = true)
    public ProjectDto.ListResponse getProjectList(Long userId) {
        var sort = Sort.by(Sort.Direction.DESC, "updatedAt");

        var items = projectRepository.findAllByUserId(userId, sort)
                .stream()
                .map(ProjectDto.ListItem::from)
                .toList();

        return new ProjectDto.ListResponse(items);
    }


    // 프로젝트 개수
    @Transactional(readOnly = true)
    public long getProjectCount(Long userId) {
        return projectRepository.countByUserId(userId);
    }

    // 프로젝트 생성
    @Transactional
    public ProjectDto.CreateResponse createProject(Long userId, ProjectDto.CreateRequest request) {

        //요청 바디 자체가 없는 경우 : 잘못된 요청
        if(request == null){
            throw new ApiException(ErrorCode.INVALID_REQUEST, "잘못된 요청 : 요청 바디가 없습니다.");
        }

        String name = request.name();
        //이름은 필수 값이며, 공백만 오는 경우도 허용하지 않음
        if(name == null || name.trim().isEmpty()){
            throw new ApiException(ErrorCode.INVALID_REQUEST, "프로젝트 이름은 필수값이다.");
        }
        name = name.trim();

        //userId기준 프로젝트 이름 중복 방지
        if(projectRepository.existsByUserIdAndName(userId, name)){
            throw new ApiException(ErrorCode.PROJECT_NAME_DUPLICATED);
        }

        //설명은 선택값. 빈 문자열을 허용하되, 앞뒤 공백은 제거하여 저장
        String description = request.description();
        if(description != null)description = description.trim();

        Project project = Project.create(
                userId,
                request.name(),
                request.description()
        );

        Project saved = projectRepository.save(project);

        return ProjectDto.CreateResponse.from(saved);
    }

    // 프로젝트 수정
    @Transactional
    public ProjectDto.UpdateResponse updateProject(Long userId, Long projectId, ProjectDto.UpdateRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }

        boolean hasAnyField = request.name() != null || request.description() != null;
        if (!hasAnyField) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "수정할 값이 없습니다.");
        }

        // 소유자 검증 포함(없으면 404)
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.PROJECT_NOT_FOUND));

        String nextName = request.name();
        if (nextName != null) {
            nextName = nextName.trim();
            if (nextName.isEmpty()) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "프로젝트 이름은 비어 있을 수 없습니다.");
            }

            // 이름 중복 방지(현재 프로젝트 제외)
            if (!nextName.equals(project.getName())
                    && projectRepository.existsByUserIdAndNameAndIdNot(userId, nextName, projectId)) {
                throw new ApiException(ErrorCode.PROJECT_NAME_DUPLICATED);
            }
        }

        String nextDescription = request.description();
        if (nextDescription != null) {
            // description은 ""로 보내면 비우기 가능
            nextDescription = nextDescription.trim();
        }

        project.update(nextName, nextDescription);

        return ProjectDto.UpdateResponse.from(project);
    }

    // 프로젝트 삭제
    @Transactional
    public void deleteProject(Long userId, Long projectId) {
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.PROJECT_NOT_FOUND));

        projectRepository.delete(project);
    }

}