-- ============================================================
-- V9 — 올린 이미지 (원단 · 스타일 사진, 2026-10-15)
--
-- 바이트는 서버 디스크(app.media.storage-dir)에, 여기에는 위치와 메타만 둔다.
-- 원단 · 스타일은 지금처럼 사진 주소(image_url)를 갖는다 — 관리자가 올리면 받은 주소(/media/...)가 그 칸에 들어간다.
-- ============================================================

CREATE TABLE media_asset (
    id                UUID        PRIMARY KEY,
    -- 표시용. 경로로 쓰지 않는다(../ 로 디렉터리를 빠져나갈 수 있다)
    original_filename TEXT        NOT NULL CHECK (char_length(original_filename) BETWEEN 1 AND 120),
    -- 브라우저가 선언한 값이 아니라 서버가 앞머리 바이트로 판별한 형식
    content_type      TEXT        NOT NULL CHECK (content_type IN ('image/png', 'image/jpeg', 'image/webp', 'image/gif', 'image/avif')),
    byte_size         BIGINT      NOT NULL CHECK (byte_size > 0),
    width             INTEGER     CHECK (width IS NULL OR width > 0),
    height            INTEGER     CHECK (height IS NULL OR height > 0),
    -- 서버가 만든 키 (날짜/uuid.확장자). 사용자 입력이 섞이지 않는다
    storage_key       TEXT        NOT NULL UNIQUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE media_asset IS '올린 이미지. 바이트는 디스크, 여기는 위치와 메타.';
