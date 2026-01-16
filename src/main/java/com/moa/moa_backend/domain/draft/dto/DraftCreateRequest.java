package com.moa.moa_backend.domain.draft.dto;

import jakarta.validation.constraints.NotBlank;

public record DraftCreateRequest (
    @NotBlank String contentPlain,
    @NotBlank String aiSource,
    @NotBlank String aiSourceUrl
){}
