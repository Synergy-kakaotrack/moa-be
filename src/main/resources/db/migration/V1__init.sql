-- 1) users
CREATE TABLE users (
                       user_id        BIGSERIAL PRIMARY KEY,
                       user_email     VARCHAR(255) NOT NULL UNIQUE,
                       user_name      VARCHAR(100) NOT NULL,
                       profile_url    VARCHAR(255) NOT NULL,
                       created_at     TIMESTAMP NOT NULL DEFAULT now()
);

-- 2) projects
CREATE TABLE projects (
                          project_id           BIGSERIAL PRIMARY KEY,
                          user_id              BIGINT NOT NULL REFERENCES users(user_id),
                          project_name         VARCHAR(100) NOT NULL,
                          project_description  TEXT NULL,
                          project_created_at   TIMESTAMP NOT NULL DEFAULT now(),
                          project_updated_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_projects_user_id ON projects(user_id);

-- 3) scraps
CREATE TABLE scraps (
                        scrap_id          BIGSERIAL PRIMARY KEY,
                        project_id        BIGINT NOT NULL REFERENCES projects(project_id),
                        user_id           BIGINT NOT NULL REFERENCES users(user_id),

                        content_plain     TEXT NOT NULL,
                        raw_html_gzip     BYTEA NOT NULL,

                        subtitle          VARCHAR(120) NOT NULL,
                        stage             VARCHAR(30)  NOT NULL,
                        memo              TEXT NULL,

                        ai_source         VARCHAR(30) NOT NULL,
                        ai_source_url     TEXT NOT NULL,

                        user_rec_project  BOOLEAN NOT NULL DEFAULT true,
                        user_rec_stage    BOOLEAN NOT NULL DEFAULT true,
                        user_rec_subtitle BOOLEAN NOT NULL DEFAULT true,

                        captured_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_scraps_u_p_s_captured
    ON scraps (user_id, project_id, stage, captured_at DESC);

CREATE INDEX idx_scraps_u_captured
    ON scraps (user_id, captured_at DESC)
    INCLUDE (project_id, stage);


-- 4) drafts
CREATE TABLE drafts (
                        draft_id         BIGSERIAL PRIMARY KEY,
                        user_id          BIGINT NOT NULL REFERENCES users(user_id),

                        rec_project_id   BIGINT NULL REFERENCES projects(project_id),

                        content_plain    TEXT NOT NULL,
                        rec_stage        VARCHAR(30) NOT NULL,
                        rec_subtitle     VARCHAR(120) NULL,

                        ai_source        VARCHAR(30) NOT NULL,
                        ai_source_url    TEXT NOT NULL,

                        draft_created_at TIMESTAMP NOT NULL DEFAULT now(),
                        draft_expired_at TIMESTAMP NOT NULL DEFAULT (now() + interval '1 hour'),

                        commit_scrap_id  BIGINT NULL,

                        rec_method       VARCHAR(20) NOT NULL DEFAULT 'LLM',

                        CONSTRAINT chk_drafts_rec_method
                            CHECK (rec_method IN ('LLM', 'FALLBACK_RECENT', 'NONE'))
);

CREATE INDEX idx_drafts_user_id        ON drafts(user_id);
CREATE INDEX idx_drafts_expired_at     ON drafts(draft_expired_at);
CREATE INDEX idx_drafts_rec_project_id ON drafts(rec_project_id);

-- drafts.commit_scrap_id -> scraps FK (scraps 생성 뒤에 추가)
ALTER TABLE drafts
    ADD CONSTRAINT fk_drafts_commit_scrap
        FOREIGN KEY (commit_scrap_id) REFERENCES scraps(scrap_id);
