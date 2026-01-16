package com.moa.moa_backend.domain.draft.entity;

import java.util.List;

public final class DraftStage {

    public static final List<String> FIXED_STAGES = List.of(
            "기획",
            "조사&분석",
            "설계",
            "구현",
            "테스트",
            "기타"
    );

    public static boolean isValid(String stage){
        return FIXED_STAGES.contains(stage);
    }

}
