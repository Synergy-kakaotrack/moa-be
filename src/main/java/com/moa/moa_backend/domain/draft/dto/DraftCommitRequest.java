package com.moa.moa_backend.domain.draft.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DraftCommitRequest(
        @NotNull Long projectId,
        @NotBlank String stage,
        @NotBlank String subtitle,

        String memo,
        String rawHtmlGzipBase64,

        @NotBlank String aiSource,
        @NotBlank String aiSourceUrl,

        boolean userRecProject,
        boolean userRecStage,
        boolean userRecSubtitle
) {}
