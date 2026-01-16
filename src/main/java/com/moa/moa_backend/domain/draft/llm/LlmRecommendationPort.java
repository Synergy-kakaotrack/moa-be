package com.moa.moa_backend.domain.draft.llm;

import com.moa.moa_backend.domain.draft.dto.DraftRecommendCommand;
import com.moa.moa_backend.domain.draft.dto.DraftRecommendation;

public interface LlmRecommendationPort {
    DraftRecommendation recommend(DraftRecommendCommand command);
}
