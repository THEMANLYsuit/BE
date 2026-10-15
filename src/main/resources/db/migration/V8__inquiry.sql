-- ============================================================
-- V8 — 문의하기
--
-- 손님이 사이트에서 보내는 문의. 회원이면 회원에 묶이고(마이페이지에서 답을 본다), 비회원은 이메일로만 답을 받는다.
-- 관리자가 답하면 ANSWERED, 더 할 말이 없으면 CLOSED. 지우지 않는다 — 같은 손님의 지난 문의가 다음 상담의 참고가 된다.
-- ============================================================

CREATE TABLE inquiry (
    id           UUID        PRIMARY KEY,
    member_id    UUID        REFERENCES member (id) ON DELETE SET NULL,
    -- 보낸 사람. 회원이면 가입 정보를 복사해 둔다(탈퇴해도 문의는 남는다)
    name         TEXT        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 50),
    email        TEXT        NOT NULL CHECK (char_length(email) BETWEEN 3 AND 254),
    phone        TEXT        CHECK (phone IS NULL OR phone ~ '^[0-9-]{9,20}$'),
    -- GENERAL(일반) · BOOKING(예약) · ORDER(진행 중 주문) · FABRIC(원단 · 스타일) · OTHER
    topic        TEXT        NOT NULL CHECK (topic IN ('GENERAL', 'BOOKING', 'ORDER', 'FABRIC', 'OTHER')),
    message      TEXT        NOT NULL CHECK (char_length(message) BETWEEN 10 AND 3000),
    -- OPEN(답 기다림) · ANSWERED(답함) · CLOSED(끝)
    status       TEXT        NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ANSWERED', 'CLOSED')),
    answer       TEXT        CHECK (answer IS NULL OR char_length(answer) <= 3000),
    answered_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT inquiry_answered_has_answer CHECK (status <> 'ANSWERED' OR answer IS NOT NULL)
);

CREATE INDEX ix_inquiry_status ON inquiry (status, created_at DESC);
CREATE INDEX ix_inquiry_member ON inquiry (member_id, created_at DESC) WHERE member_id IS NOT NULL;

COMMENT ON TABLE inquiry IS '손님 문의. 회원 · 비회원 모두. 관리자가 답하면 손님에게 메일.';

CREATE TRIGGER trg_inquiry_updated_at
    BEFORE UPDATE ON inquiry
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
