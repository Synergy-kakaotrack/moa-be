package com.moa.moa_backend.domain.draft.repository;

import com.moa.moa_backend.domain.draft.entity.Draft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DraftRepository extends JpaRepository<Draft, Long> {


    Optional<Draft> findByIdAndUserId(Long Id, Long userId);

    long deleteByIdAndUserId(Long Id, Long userId);


}
