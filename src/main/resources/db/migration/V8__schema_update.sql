CREATE TABLE IF NOT EXISTS project_digests (
    project_digest_id        BIGSERIAL PRIMARY KEY,

    project_id               BIGINT NOT NULL,
    user_id                  BIGINT NOT NULL,

    -- DEFAULT / CUSTOM (현재 활성 요약의 종류)
    digest_kind              VARCHAR(10) NOT NULL DEFAULT 'DEFAULT',

    -- CUSTOM일 때만 값 존재
    prompt_text              TEXT,

    -- 프로젝트 요약 결과
    digest_text              TEXT,

    -- 이 요약이 반영한 프로젝트 스크랩들의 최신 시각
    source_last_updated_at   TIMESTAMPTZ,

    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
    );

-- digest_kind 제한
ALTER TABLE project_digests
    ADD CONSTRAINT project_digests_kind_chk
        CHECK (digest_kind IN ('DEFAULT', 'CUSTOM'));

-- CUSTOM이면 prompt_text 필수
ALTER TABLE project_digests
    ADD CONSTRAINT project_digests_custom_prompt_chk
        CHECK (
            digest_kind <> 'CUSTOM'
                OR (prompt_text IS NOT NULL AND length(btrim(prompt_text)) > 0)
            );

-- FK
ALTER TABLE project_digests
    ADD CONSTRAINT fk_project_digests_project
        FOREIGN KEY (project_id) REFERENCES projects(project_id)
            ON DELETE CASCADE;

ALTER TABLE project_digests
    ADD CONSTRAINT fk_project_digests_user
        FOREIGN KEY (user_id) REFERENCES users(user_id)
            ON DELETE CASCADE;

-- 프로젝트당 요약은 딱 1개
CREATE UNIQUE INDEX IF NOT EXISTS uq_project_digests_one_per_project
    ON project_digests (user_id, project_id);

CREATE INDEX IF NOT EXISTS idx_project_digests_lookup
    ON project_digests (user_id, project_id);

