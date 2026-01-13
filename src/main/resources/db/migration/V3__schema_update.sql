-- scraps 테이블에서 content_plain 제거 (최종 저장은 raw_html_gzip만 유지)

ALTER TABLE scraps
    DROP COLUMN IF EXISTS content_plain;