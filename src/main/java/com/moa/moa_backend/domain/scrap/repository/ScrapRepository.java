package com.moa.moa_backend.domain.scrap.repository;

import com.moa.moa_backend.domain.scrap.entity.Scrap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScrapRepository extends JpaRepository<Scrap, Long> {
    Optional<Scrap> findFirstByUserIdOrderByCapturedAtDesc(Long userId);
}
