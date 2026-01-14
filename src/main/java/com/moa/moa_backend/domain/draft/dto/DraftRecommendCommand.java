package com.moa.moa_backend.domain.draft.dto;

import java.time.Instant;
import java.util.List;

//추천을 요청하기 위해 필요한 입력 데이터 묶음
//service에서는 정보들을 모아 이 command를 만들고 port로 전달함
public record DraftRecommendCommand(
        Long userId,
        String contentPlain,
        String sourceCode,
        String sourceUrl,

        List<ProjectOption> projects,
        List<String> fixedStages,

        RecentContext recentContext,
        Instant now
) {
    //사용자가 가진 프로젝트 범위 안에서만 고르도록 해야함
    public record ProjectOption(Long projectId, String name) {}
    //작업단계는 프론트에서 고정목록을 갖고 있고 서버는 받아서 저장하는 형태
    //추천단계에서는 LLM/어댑터도 선택가능한 stage를 알아야함
    public record RecentContext(Long projectId, String stage, Instant capturedAt) {}
}
