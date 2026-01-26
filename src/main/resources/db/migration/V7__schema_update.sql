-- V7: stage_digests digest_json -> digest_text(TEXT) 로 변경
BEGIN;

-- 1. 기존 컬럼 제거
ALTER TABLE stage_digests
DROP COLUMN digest_json;

-- 2. TEXT 컬럼 추가
ALTER TABLE stage_digests
    ADD COLUMN digest_text TEXT;

COMMIT;
