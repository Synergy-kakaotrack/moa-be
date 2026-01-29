package com.moa.moa_backend.domain.digest.llm;

import com.moa.moa_backend.domain.digest.entity.DigestKind;
import com.moa.moa_backend.domain.scrap.repository.projection.ScrapForDigestView;

import java.util.List;

public interface ProjectDigestGeneratorPort {

    /**
     * 프로젝트 전체 요약을 마크다운으로 생성한다.
     *
     * @param projectName  프로젝트 이름 (프롬프트 컨텍스트)
     * @param kind         DEFAULT | CUSTOM
     * @param customPrompt CUSTOM일 때만 사용 (null 가능)
     * @param scraps       digest 입력용 스크랩(정규화 전/후 모두 가능하지만, Adapter에서 안전하게 정규화한다)
     */
    String generateMarkdown(
            String projectName,
            DigestKind kind,
            String customPrompt,
            List<ScrapForDigestView> scraps
    );
}
