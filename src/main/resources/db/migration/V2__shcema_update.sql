-- V2__schema_update.sql
-- 1) scraps: 추천 방식(rec_method) 저장 (장기 분석용)
ALTER TABLE scraps
    ADD COLUMN rec_method VARCHAR(20) NOT NULL DEFAULT 'LLM';

ALTER TABLE scraps
    ADD CONSTRAINT chk_scraps_rec_method
        CHECK (rec_method IN ('LLM', 'FALLBACK_RECENT', 'NONE'));


-- 2) drafts: commit_scrap_id 제거 (commit 성공 시 draft 삭제 정책)
-- 혹시 FK 제약이 존재하면 먼저 제거
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_drafts_commit_scrap'
  ) THEN
ALTER TABLE drafts DROP CONSTRAINT fk_drafts_commit_scrap;
END IF;
END $$;

ALTER TABLE drafts
DROP COLUMN IF EXISTS commit_scrap_id;
