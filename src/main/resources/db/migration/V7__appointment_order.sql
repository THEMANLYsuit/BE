-- ============================================================
-- V7 — 예약을 맞춤 주문에 묶는다
--
-- 가봉 · 수령 예약은 어느 주문의 것인지가 중요하다 — 관리자 화면에서 "이 주문의 가봉이 언제인가" 를 바로 본다.
-- 첫 상담처럼 주문 전 예약은 비어 있다. 주문이 지워지면(지금은 지우지 않지만) 연결만 끊긴다.
-- ============================================================

ALTER TABLE appointment
    ADD COLUMN order_id UUID REFERENCES bespoke_order (id) ON DELETE SET NULL;

CREATE INDEX ix_appointment_order ON appointment (order_id) WHERE order_id IS NOT NULL;

COMMENT ON COLUMN appointment.order_id IS '가봉 · 수령 예약이 속한 맞춤 주문. 상담 예약은 NULL.';
