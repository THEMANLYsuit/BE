-- ============================================================
-- V11 — 메인 스크롤 필름 (2026-10-16 "관리자에 영상 등록하면 프레임 단위로 끊어 줄 수 있는")
--
-- 관리자가 영상을 올리면 서버가 ffmpeg 로 사진 묶음(1초 12장, 가로 1280, WebP)을 만든다 — AI 아님, 영상 변환 도구.
-- 바이트는 디스크(app.media.storage-dir/film/{id}/f001.webp …)에, 여기에는 몇 장인지만 둔다.
-- 지금 메인에 걸리는 것은 active 한 줄뿐이다(새로 올리면 그것으로 바뀐다). 지난 것은 남겨 두어 되돌릴 수 있다.
-- ============================================================

CREATE TABLE film (
    id                UUID        PRIMARY KEY,
    frame_count       INTEGER     NOT NULL CHECK (frame_count BETWEEN 1 AND 600),
    fps               SMALLINT    NOT NULL CHECK (fps BETWEEN 1 AND 30),
    -- 표시용. 경로로 쓰지 않는다
    original_filename TEXT        NOT NULL CHECK (char_length(original_filename) BETWEEN 1 AND 120),
    byte_size         BIGINT      NOT NULL CHECK (byte_size > 0),
    active            BOOLEAN     NOT NULL DEFAULT FALSE,
    created_by        UUID        REFERENCES member (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 메인에 걸리는 필름은 하나뿐이다
CREATE UNIQUE INDEX uq_film_active ON film (active) WHERE active;

COMMENT ON TABLE film IS '메인 스크롤 필름 — 영상에서 뽑은 사진 묶음. active 한 줄이 메인에 걸린다.';
