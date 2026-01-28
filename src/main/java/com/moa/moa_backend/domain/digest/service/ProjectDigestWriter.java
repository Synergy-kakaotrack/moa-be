package com.moa.moa_backend.domain.digest.service;

import com.moa.moa_backend.domain.digest.entity.DigestKind;
import com.moa.moa_backend.domain.digest.entity.ProjectDigest;
import com.moa.moa_backend.domain.digest.repository.ProjectDigestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ProjectDigestWriter {

    private final ProjectDigestRepository projectDigestRepository;

    /**
     * DB upsert 전용 (짧게 트랜잭션)
     * - LLM 호출은 Service에서 처리하고, 여기서는 "쓰기"만 담당
     * - (userId, projectId) 유니크 1건 유지
     */
    @Transactional
    public ProjectDigest upsertDigest(
            Long userId,
            Long projectId,
            DigestKind kind,
            String promptText,          // CUSTOM일 때만 값, DEFAULT면 null 권장
            String digestText,          // 저장할 markdown
            Instant sourceLastUpdatedAt // 이번 digest가 반영한 최신 스크랩 시각
    ) {
        ProjectDigest digest = projectDigestRepository
                .findByUserIdAndProjectId(userId, projectId)
                .orElseGet(() -> ProjectDigest.createEmpty(userId, projectId));

        digest.update(
                kind,
                (promptText == null || promptText.isBlank()) ? null : promptText.trim(),
                digestText,
                sourceLastUpdatedAt
        );

        return projectDigestRepository.save(digest);
    }
}

