package com.moa.moa_backend.domain.project.dto;

import com.moa.moa_backend.domain.project.entity.Project;

import java.time.Instant;

public class ProjectDto {

    private ProjectDto(){}

    public record ListItem(
            Long id,
            String name,
            String description,
            Instant updatedAt
    ){
        public static ListItem from(Project project){
            return new ListItem(
                    project.getId(),
                    project.getName(),
                    project.getDescription(),
                    project.getUpdatedAt()
            );
        }
    }
}