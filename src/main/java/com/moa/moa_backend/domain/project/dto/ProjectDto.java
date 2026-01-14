package com.moa.moa_backend.domain.project.dto;

import com.moa.moa_backend.domain.project.entity.Project;

import java.time.Instant;

public record ProjectDto(
        Long id,
        String name,
        String description,
        Instant updatedAt
) {
    public static ProjectDto from(Project p) {
        return new ProjectDto(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getUpdatedAt()
        );
    }
}
