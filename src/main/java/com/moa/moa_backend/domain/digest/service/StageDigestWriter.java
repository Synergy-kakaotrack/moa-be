package com.moa.moa_backend.domain.digest.service;

import com.moa.moa_backend.domain.digest.entity.StageDigest;
import com.moa.moa_backend.domain.digest.repository.StageDigestRepository;
import com.moa.moa_backend.global.error.ApiException;
import com.moa.moa_backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class StageDigestWriter {

    private final StageDigestRepository stageDigestRepository;

    /**
     * (userId, projectId, stage) 유니크 기준으로 Digest upsert
     * - 트랜잭션은 "DB write" 구간만 짧게 잡는다.
     * - 동시 insert 경쟁 시 DataIntegrityViolationException을 잡아서 update로 재시도한다.
     */
    @Transactional
    public StageDigest upsertDigest(
            Long userId,
            Long projectId,
            String stage,
            String markdown,
            OffsetDateTime sourceLastCapturedAt
    ) {
        // 1) 있으면 update
        Optional<StageDigest> existingOpt =
                stageDigestRepository.findByUserIdAndProjectIdAndStage(userId, projectId, stage);

        if (existingOpt.isPresent()) {
            StageDigest existing = existingOpt.get();
            existing.updateDigest(markdown, sourceLastCapturedAt);
            return stageDigestRepository.save(existing);
        }

        // 2) 없으면 insert 시도 (유니크 경쟁 가능)
        try {
            StageDigest created = StageDigest.create(userId, projectId, stage, null, sourceLastCapturedAt);
            created.updateDigest(markdown, sourceLastCapturedAt);

            // 여기서 flush를 쓰면 "유니크 위반"을 이 try-catch 안에서 더 확실히 잡을 수 있음
            // (commit 시점까지 밀리면 catch가 바깥으로 새어 나갈 수 있음)
            return stageDigestRepository.saveAndFlush(created);

        } catch (DataIntegrityViolationException e) {
            // 3) 누군가가 나보다 먼저 insert한 경우 -> update로 전환
            log.warn("[DIGEST] upsert race detected. retry update. userId={}, projectId={}, stage={}",
                    userId, projectId, stage, e);

            StageDigest nowExisting = stageDigestRepository
                    .findByUserIdAndProjectIdAndStage(userId, projectId, stage)
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR));

            nowExisting.updateDigest(markdown, sourceLastCapturedAt);
            return stageDigestRepository.save(nowExisting);
        }
    }
}
