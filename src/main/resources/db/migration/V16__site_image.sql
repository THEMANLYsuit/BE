-- ============================================================
-- V16 — 메인 사진 칸 (2026-11-02 디자이너 새 메인)
--
-- 새 메인의 사진을 관리자에서 바꾼다. 칸은 정해져 있다:
--   BODIES  세 체형(상의 탈의) — 마른 체형 · 운동 체형 · 넉넉한 체형이 가로 한 장에
--   SUITS   같은 세 사람이 맞춤 수트를 입은 가로 한 장
--   EMBLEM  왼쪽 위 방패 엠블럼
--   PROCESS 제작 순서 화면의 세로 사진
--   SHOP    오프라인 매장 화면의 사진
-- 사진 바이트는 올린 이미지(media_asset)에 있고, 여기는 어느 칸에 무엇을 걸었는지만 둔다. 칸이 비면 손님 화면은 자리표시.
-- 메인 영상은 메인 필름(V11)이 맡는다 — 올리면 반복 재생 영상과 첫 장면 사진도 함께 만든다.
-- 손님 화면은 빌드할 때 받는다 — 바꾸면 다시 만든다(FrontRebuildTrigger).
-- ============================================================

CREATE TABLE site_image (
    slot        TEXT        PRIMARY KEY CHECK (slot IN ('BODIES', 'SUITS', 'EMBLEM', 'PROCESS', 'SHOP')),
    media_id    UUID        NOT NULL REFERENCES media_asset (id),
    -- 대체 텍스트 — 사진이 무엇을 보여 주는지(스크린리더가 읽는다)
    alt         TEXT        NOT NULL CHECK (char_length(alt) BETWEEN 1 AND 200),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE site_image IS '메인 사진 칸 — 칸마다 올린 이미지 한 장. 비면 손님 화면은 자리표시.';

CREATE TRIGGER trg_site_image_updated_at
    BEFORE UPDATE ON site_image
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
