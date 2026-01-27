-- V6__timestamp_to_timestamptz_kst.sql

-- 0) 마이그레이션 중 혼선 방지
SET TIME ZONE 'Asia/Seoul';

-- 1) users.created_at
ALTER TABLE users
ALTER COLUMN created_at TYPE TIMESTAMPTZ
  USING (created_at AT TIME ZONE 'Asia/Seoul');

ALTER TABLE users
    ALTER COLUMN created_at SET DEFAULT now();


-- 2) projects
ALTER TABLE projects
ALTER COLUMN project_created_at TYPE TIMESTAMPTZ
  USING (project_created_at AT TIME ZONE 'Asia/Seoul');

ALTER TABLE projects
ALTER COLUMN project_updated_at TYPE TIMESTAMPTZ
  USING (project_updated_at AT TIME ZONE 'Asia/Seoul');

ALTER TABLE projects
    ALTER COLUMN project_created_at SET DEFAULT now();

ALTER TABLE projects
    ALTER COLUMN project_updated_at SET DEFAULT now();


-- 3) drafts
ALTER TABLE drafts
ALTER COLUMN draft_created_at TYPE TIMESTAMPTZ
  USING (draft_created_at AT TIME ZONE 'Asia/Seoul');

ALTER TABLE drafts
ALTER COLUMN draft_expired_at TYPE TIMESTAMPTZ
  USING (draft_expired_at AT TIME ZONE 'Asia/Seoul');

ALTER TABLE drafts
    ALTER COLUMN draft_created_at SET DEFAULT now();

ALTER TABLE drafts
    ALTER COLUMN draft_expired_at SET DEFAULT (now() + interval '1 hour');

-- 4) stage_digests (신규)
CREATE TABLE IF NOT EXISTS stage_digests (
    stage_digest_id         BIGSERIAL PRIMARY KEY,

    project_id              BIGINT NOT NULL,
    user_id                 BIGINT NOT NULL,

    stage                   VARCHAR(30) NOT NULL,
    digest_json             JSONB NOT NULL,

    -- 요약 최신성 판단용: 요약 생성 시점에 반영된 스크랩 중 가장 최신 captured_at
    source_last_captured_at TIMESTAMPTZ NULL,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_stage_digests UNIQUE (user_id, project_id, stage),

    CONSTRAINT fk_stage_digests_project
    FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,

    CONSTRAINT fk_stage_digests_user
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_stage_digests_user_project
    ON stage_digests (user_id, project_id);

CREATE INDEX IF NOT EXISTS idx_stage_digests_updated_at
    ON stage_digests (updated_at DESC);