package com.moa.moa_backend.domain.digest.llm;

import com.moa.moa_backend.domain.scrap.repository.projection.ScrapForDigestView;

import java.util.List;

/**
 * Stage Digest 생성 Port
 * - 입력(프로젝트명, stage, 스크랩들) -> 출력(markdown text)
 */
public interface StageDigestGeneratorPort {
    String generateMarkdown(String projectName, String stage, List<ScrapForDigestView> scraps);
}
