package com.moa.moa_backend.domain.scrap.controller;

import com.moa.moa_backend.domain.scrap.dto.ScrapDetailResponse;
import com.moa.moa_backend.domain.scrap.dto.ScrapListResponse;
import com.moa.moa_backend.domain.scrap.dto.ScrapRecentContextResponse;
import com.moa.moa_backend.domain.scrap.service.ScrapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Scrap API", description = "스크랩 목록/상세/최근 컨텍스트 조회 API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/scraps")
public class ScrapController {

    private final ScrapService scrapService;

    @Operation(summary = "스크랩 목록 조회", description = "projectId + stage 조건으로 cursor 기반 무한 스크롤 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ScrapListResponse> list(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam Long projectId,
            @RequestParam String stage,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "50") Integer limit
    ) {
        return ResponseEntity.ok(
                scrapService.getScrapList(userId, projectId, stage, cursor, limit)
        );
    }

    @Operation(summary = "스크랩 상세 조회", description = "scrapId로 스크랩 상세를 조회합니다.")
    @GetMapping("/{scrapId}")
    public ResponseEntity<ScrapDetailResponse> detail(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long scrapId
    ) {
        return ResponseEntity.ok(scrapService.getScrapDetail(userId, scrapId));
    }

    @Operation(summary = "최근 저장 컨텍스트 조회", description = "프로젝트별 최신 스크랩 기준으로 최근 3개 컨텍스트를 반환합니다.")
    @GetMapping("/recent-context")
    public ResponseEntity<ScrapRecentContextResponse> recentContext(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(scrapService.getRecentContext(userId));
    }
}
