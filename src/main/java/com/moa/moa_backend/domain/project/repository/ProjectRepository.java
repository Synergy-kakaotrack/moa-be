package com.moa.moa_backend.domain.project.repository;

import com.moa.moa_backend.domain.project.entity.Project;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByUserId(Long userId, Sort sort);

    long countByUserId(Long userId);

    Optional<Project> findByIdAndUserId(Long projectId, Long userId);

    boolean existsByUserIdAndNameAndIdNot(Long userId, String name, Long id);
}