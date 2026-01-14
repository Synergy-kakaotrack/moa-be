package com.moa.moa_backend.domain.draft.entity;

public enum RecMethod {
    LLM, FALLBACK_RECENT, NONE
}
/*
* 1) 프로젝트 있음 + LLM 성공
* recMethod = LLM
* projectId , stage , subtitle
2) 프로젝트 없음 + LLM 성공
* recMethod = LLM
* projectId (null), stage , subtitle  (LLM은 성공했지만 프로젝트가 없어서 추천 불가)
3) 프로젝트 있음 + LLM 실패
* recMethod = FALLBACK_RECENT
* projectId (recent 있으면 recent, 없으면 첫 프로젝트)
* stage (recent stage or fixedStages[0])
* subtitle (null)
4) 프로젝트 없음 + LLM 실패 >>>>> NONE
* recMethod = NONE
* projectId (null)
* stage (fixedStages[0] 또는 recent stage가 있다면? → 여기서도 “프로젝트 없음”이니 recentContext 자체가 없을 가능성이 큼)
* subtitle (null)

 */