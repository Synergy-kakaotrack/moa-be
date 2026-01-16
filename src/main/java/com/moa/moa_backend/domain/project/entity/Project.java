package com.moa.moa_backend.domain.project.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Table(name = "projects")
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "project_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "project_name", nullable = false, length = 100)
    private String name;

    @Column(name = "project_description")
    private String description;

    @Column(name = "project_created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "project_updated_at", nullable = false)
    private Instant updatedAt;

    // 프로젝트 생성
    public static Project create(Long userId, String name, String description) {
        Project project = new Project();
        project.userId = userId;
        project.name = name;
        project.description = description;
        return project;
    }

    // 프로젝트 수정
    public void update(String name, String description) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
    }

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = Instant.now();
    }

}
