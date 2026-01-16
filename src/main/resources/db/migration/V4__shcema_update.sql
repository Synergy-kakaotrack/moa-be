-- V4
-- scraps 테이블 재생성
DROP TABLE IF EXISTS scraps CASCADE;

CREATE TABLE scraps (
                        scrap_id          BIGSERIAL PRIMARY KEY,
                        project_id        BIGINT NOT NULL REFERENCES projects(project_id) ON DELETE CASCADE,
                        user_id           BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,

    -- 원본 HTML (text로 저장)
                        raw_html          TEXT NOT NULL,

    -- 메타데이터
                        subtitle          VARCHAR(500) NOT NULL,
                        stage             VARCHAR(50)  NOT NULL,
                        memo              TEXT NULL,

    -- 추가 정보
                        ai_source      VARCHAR(30) NOT NULL,
                        ai_source_url        TEXT NOT NULL,

    -- 추천 관련
                        rec_method        VARCHAR(50) NOT NULL DEFAULT 'LLM',
                        user_rec_project  BOOLEAN NOT NULL DEFAULT false,
                        user_rec_stage    BOOLEAN NOT NULL DEFAULT false,
                        user_rec_subtitle BOOLEAN NOT NULL DEFAULT false,

    -- 타임스탬프
                        captured_at       TIMESTAMP NOT NULL,
                        created_at        TIMESTAMP NOT NULL DEFAULT now(),
                        updated_at        TIMESTAMP NOT NULL DEFAULT now(),

                        CONSTRAINT chk_scraps_rec_method
                            CHECK (rec_method IN ('LLM', 'FALLBACK_RECENT', 'NONE'))
);

-- 인덱스
CREATE INDEX idx_scraps_user_captured ON scraps (user_id, captured_at DESC);
CREATE INDEX idx_scraps_project_captured ON scraps (project_id, captured_at DESC);
CREATE INDEX idx_scraps_user_project_stage ON scraps (user_id, project_id, stage, captured_at DESC);

-- 전문 검색 인덱스
-- CREATE INDEX idx_scraps_fts ON scraps
-- USING GIN (to_tsvector('english', coalesce(subtitle, '') || ' ' || coalesce(memo, '')));

-- drafts 테이블은 변경 불필요 (content_plain만 저장)
-- commit 시점에 raw_html을 새로 받아서 scraps에 저장