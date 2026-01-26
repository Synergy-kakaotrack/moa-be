package com.moa.moa_backend.domain.digest.controller;

import com.moa.moa_backend.domain.digest.dto.StageDigestResponse;
import com.moa.moa_backend.domain.digest.service.StageDigestService;
import org.springframework.web.bind.annotation.*;

/**
 * 작업단계 요약 API
 * - refresh는 body 없이 호출(버튼 트리거)
 */
@RestController
@RequestMapping("/api/projects/{projectId}/stages/{stage}")
public class StageDigestController {

    private final StageDigestService service;

    public StageDigestController(StageDigestService service) {
        this.service = service;
    }

    @GetMapping("/digest")
    public StageDigestResponse getDigest(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long projectId,
            @PathVariable String stage
    ) {
        return service.getDigest(userId, projectId, stage);
    }

    @PostMapping("/digest:refresh")
    public StageDigestResponse refresh(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long projectId,
            @PathVariable String stage
    ) {
        return service.refresh(userId, projectId, stage);
    }
}
