package com.moa.moa_backend.domain.project.dto;

import com.moa.moa_backend.domain.project.entity.Project;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProjectDto {

    private ProjectDto(){}

    // 프로젝트 목록
    public record ListItem(
            Long projectId,
            String name,
            String description
//            Instant updatedAt
    ){
        public static ListItem from(Project project){
            return new ListItem(
                    project.getId(),
                    project.getName(),
                    project.getDescription()
//                    project.getUpdatedAt()
            );
        }
    }
    public record ListResponse(
            java.util.List<ListItem> items
    ) { }

    // 프로젝트 생성 요청
    public record CreateRequest(
            @NotBlank(message = "프로젝트 이름은 필수입니다.")
            @Size(max = 100, message = "프로젝트 이름은 100자 이하여야 합니다.")
            String name,

            @Size(max = 1000, message = "프로젝트 설명은 1000자 이하여야 합니다.")
            String description
    ) { }

    // 프로젝트 생성 응답
    public record CreateResponse(
            Long projectId
//            String name,
//            String description,
//            Instant createdAt,
//            Instant updatedAt
    ) {
        public static CreateResponse from(Project project) {
            return new CreateResponse(
                    project.getId()
//                    project.getName(),
//                    project.getDescription(),
//                    project.getCreatedAt(),
//                    project.getUpdatedAt()
            );
        }
    }

    // 프로젝트 수정
    public record UpdateRequest(
            @Size(max = 100, message = "프로젝트 이름은 100자 이하여야 합니다.")
            String name,

            @Size(max = 1000, message = "프로젝트 설명은 1000자 이하여야 합니다.")
            String description
    ) { }

    public record UpdateResponse(
            Long projectId
//            String name,
//            String description,
//            Instant createdAt,
//            Instant updatedAt
    ) {
        public static UpdateResponse from(Project project) {
            return new UpdateResponse(
                    project.getId()
//                    project.getName(),
//                    project.getDescription(),
//                    project.getCreatedAt(),
//                    project.getUpdatedAt()
            );
        }
    }

    // 프로젝트 개수 응답
    public record CountResponse(
            long count
    ) { }
}