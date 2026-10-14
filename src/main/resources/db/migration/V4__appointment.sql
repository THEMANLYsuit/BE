-- ============================================================
-- V4 — 상담 · 가봉 예약
--
-- 손님이 날짜 · 시간 · 목적을 골라 신청하면 REQUESTED 로 들어오고, 관리자가 확인하면 CONFIRMED 가 된다.
-- 자리(시간)가 겹치는지는 서버(BookingService)가 본다 — 같은 시작 시각에 살아 있는(REQUESTED · CONFIRMED) 예약은 하나.
-- 지난 예약은 지우지 않는다 — 손님 이력이고, 노쇼 · 취소 기록이 다음 상담의 참고가 된다.
-- ============================================================

CREATE TABLE appointment (
    id                UUID        PRIMARY KEY,
    member_id         UUID        NOT NULL REFERENCES member (id),
    -- CONSULTATION(첫 상담) · FITTING(가봉) · PICKUP(수령) · OTHER
    type              TEXT        NOT NULL CHECK (type IN ('CONSULTATION', 'FITTING', 'PICKUP', 'OTHER')),
    -- REQUESTED(신청) · CONFIRMED(확정) · CANCELLED(취소) · COMPLETED(완료) · NO_SHOW(불참)
    status            TEXT        NOT NULL DEFAULT 'REQUESTED'
                      CHECK (status IN ('REQUESTED', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    starts_at         TIMESTAMPTZ NOT NULL,
    duration_minutes  SMALLINT    NOT NULL CHECK (duration_minutes BETWEEN 15 AND 240),
    -- 손님이 적는 메모 (원하는 품목 · 입을 자리 · 희망 시기 등)
    customer_note     TEXT        NOT NULL DEFAULT '' CHECK (char_length(customer_note) <= 1000),
    -- 운영자 메모 (손님 화면에 보이지 않는다)
    admin_note        TEXT        NOT NULL DEFAULT '' CHECK (char_length(admin_note) <= 1000),
    -- 누가 취소했나 · 왜. CANCELLED 일 때만
    cancelled_by      TEXT        CHECK (cancelled_by IS NULL OR cancelled_by IN ('CUSTOMER', 'ADMIN')),
    cancel_reason     TEXT        CHECK (cancel_reason IS NULL OR char_length(cancel_reason) <= 300),
    confirmed_at      TIMESTAMPTZ,
    cancelled_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 살아 있는 예약은 같은 시각에 하나 — 두 손님이 같은 자리를 동시에 누르는 경쟁을 DB 가 마지막으로 막는다
CREATE UNIQUE INDEX uq_appointment_live_slot ON appointment (starts_at)
    WHERE status IN ('REQUESTED', 'CONFIRMED');

CREATE INDEX ix_appointment_member ON appointment (member_id, starts_at DESC);
CREATE INDEX ix_appointment_starts ON appointment (starts_at);

COMMENT ON TABLE appointment IS '상담 · 가봉 · 수령 예약. 살아 있는 예약은 시작 시각이 겹치지 않는다.';

CREATE TRIGGER trg_appointment_updated_at
    BEFORE UPDATE ON appointment
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
