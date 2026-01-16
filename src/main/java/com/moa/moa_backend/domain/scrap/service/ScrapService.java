package com.moa.moa_backend.domain.scrap.service;

import com.moa.moa_backend.domain.draft.dto.DraftCommitRequest;
import com.moa.moa_backend.domain.draft.entity.DraftStage;
import com.moa.moa_backend.domain.draft.entity.RecMethod;
import com.moa.moa_backend.domain.project.repository.ProjectRepository;
import com.moa.moa_backend.domain.scrap.entity.Scrap;
import com.moa.moa_backend.domain.scrap.repository.ScrapRepository;
import com.moa.moa_backend.global.error.ApiException;
import com.moa.moa_backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Transactional
@RequiredArgsConstructor
@Service
public class ScrapService {

    private final ScrapRepository scrapRepository;
    private final ProjectRepository projectRepository;

    public Long createFromDraftCommit(
            Long userId,
            DraftCommitRequest req,
            RecMethod recMethod,
            Instant capturedAt
    ) {
        // 프로젝트 존재 + 소유자 검증
        if (req.projectId() == null || !projectRepository.existsByIdAndUserId(req.projectId(), userId)) {
            throw new ApiException(ErrorCode.PROJECT_NOT_FOUND);
        }

        // stage 검증
        if (req.stage() == null || !DraftStage.isValid(req.stage())) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "유효하지 않은 작업 단계입니다: " + req.stage()
            );
        }

        // 필수 문자열 검증
        String rawHtml = normalizeRequired(req.rawHtml(), "rawHtml");
        String subtitle = normalizeRequired(req.subtitle(), "subtitle");
        String aiSource = normalizeRequired(req.aiSource(), "aiSource");
        String aiSourceUrl = normalizeRequired(req.aiSourceUrl(), "aiSourceUrl");

        boolean userRecProject = Boolean.TRUE.equals(req.userRecProject());
        boolean userRecStage = Boolean.TRUE.equals(req.userRecStage());
        boolean userRecSubtitle = Boolean.TRUE.equals(req.userRecSubtitle());

        Scrap scrap = Scrap.create(
                req.projectId(),
                userId,
                rawHtml,
                subtitle,
                req.stage(),
                req.memo(),
                aiSource,
                aiSourceUrl,
                userRecProject,
                userRecStage,
                userRecSubtitle,
                capturedAt,
                recMethod
        );

        return scrapRepository.save(scrap).getId();
    }

    private static String normalizeRequired(String v, String fieldName) {
        if (v == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, fieldName + "은(는) 필수입니다.");
        }
        String t = v.trim();
        if (t.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, fieldName + "은(는) 빈 문자열일 수 없습니다.");
        }
        return t;
    }
}

