-- ============================================================
-- V6 — 맞춤 주문과 진행 단계
--
-- 상담에서 주문이 정해지면 관리자가 만든다(손님이 화면에서 주문하지 않는다 — 값과 선택은 상담에서 정한다).
-- 단계는 상담 → 치수 → 원단 · 스타일 → 가봉 → 수정 · 완성 → 수령. 가봉을 한 번 더 하는 일이 있어 뒤로도 옮긴다.
-- 단계가 바뀌거나 안내를 남길 때마다 bespoke_order_event 에 한 줄 — 손님 마이페이지의 진행 기록이 된다.
--
-- 결제는 매장에서 받는다. 여기는 금액(총액 · 받은 계약금)을 적어 둘 뿐이다.
-- ============================================================

CREATE TABLE bespoke_order (
    id              UUID        PRIMARY KEY,
    -- 손님에게 보이는 번호. 예) TM20261014-K7MQ2XRA — 날짜 + 무작위 8자(순번이면 주문 수가 샌다)
    order_number    TEXT        NOT NULL UNIQUE CHECK (order_number ~ '^TM[0-9]{8}-[A-Z2-9]{8}$'),
    member_id       UUID        NOT NULL REFERENCES member (id),
    -- 품목
    garment         TEXT        NOT NULL CHECK (garment IN
                        ('SUIT_2PC', 'SUIT_3PC', 'JACKET', 'TROUSERS', 'VEST', 'COAT', 'SHIRT', 'OTHER')),
    -- 주문 이름 (예: 결혼식 네이비 수트). 손님에게 보인다
    title           TEXT        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 80),
    fabric_id       UUID        REFERENCES fabric (id),
    -- 원단 메모 (손님이 가져온 원단 · 안감 색 등)
    fabric_note     TEXT        NOT NULL DEFAULT '' CHECK (char_length(fabric_note) <= 200),
    -- 이 주문에 쓴 치수 기록. 치수를 지우면(탈퇴 등) 끊긴다
    measurement_id  UUID        REFERENCES measurement (id) ON DELETE SET NULL,
    -- CONSULTATION(상담) · MEASUREMENT(치수) · FABRIC(원단 · 스타일) · FITTING(가봉) · ADJUSTMENT(수정 · 완성) · DELIVERY(수령)
    stage           TEXT        NOT NULL DEFAULT 'CONSULTATION' CHECK (stage IN
                        ('CONSULTATION', 'MEASUREMENT', 'FABRIC', 'FITTING', 'ADJUSTMENT', 'DELIVERY')),
    -- IN_PROGRESS(진행 중) · COMPLETED(수령 완료) · CANCELLED(취소)
    status          TEXT        NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    -- 총액. 상담 전이면 NULL
    price_krw       BIGINT      CHECK (price_krw IS NULL OR price_krw >= 0),
    -- 받은 계약금
    deposit_krw     BIGINT      NOT NULL DEFAULT 0 CHECK (deposit_krw >= 0),
    -- 수령 예정일
    due_on          DATE,
    -- 운영자 메모 — 손님에게 보이지 않는다
    admin_note      TEXT        NOT NULL DEFAULT '' CHECK (char_length(admin_note) <= 2000),
    cancel_reason   TEXT        CHECK (cancel_reason IS NULL OR char_length(cancel_reason) <= 300),
    completed_at    TIMESTAMPTZ,
    cancelled_at    TIMESTAMPTZ,
    -- 마지막으로 진행 기록이 붙은 때 — 관리자 목록의 정렬 기준
    last_event_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (price_krw IS NULL OR deposit_krw <= price_krw)
);

CREATE INDEX ix_bespoke_order_member ON bespoke_order (member_id, created_at DESC);
CREATE INDEX ix_bespoke_order_status ON bespoke_order (status, last_event_at DESC);

COMMENT ON TABLE bespoke_order IS '맞춤 주문. 관리자가 상담 뒤에 만든다. 결제는 매장에서 — 금액만 적는다.';

CREATE TRIGGER trg_bespoke_order_updated_at
    BEFORE UPDATE ON bespoke_order
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- 고른 스타일 — 묶음마다 하나 (예: LAPEL → peak). 값은 style_option.code
CREATE TABLE bespoke_order_style (
    order_id     UUID NOT NULL REFERENCES bespoke_order (id) ON DELETE CASCADE,
    category     TEXT NOT NULL CHECK (category IN (
                     'JACKET_BUTTONS', 'LAPEL', 'VENT', 'POCKET', 'LINING',
                     'TROUSER_PLEAT', 'TROUSER_HEM', 'WAIST', 'MONOGRAM')),
    option_code  TEXT NOT NULL CHECK (option_code ~ '^[a-z0-9-]{2,40}$'),
    PRIMARY KEY (order_id, category)
);


-- 진행 기록. 손님에게 보인다 — 운영자만 볼 말은 bespoke_order.admin_note 에.
CREATE TABLE bespoke_order_event (
    id          UUID        PRIMARY KEY,
    order_id    UUID        NOT NULL REFERENCES bespoke_order (id) ON DELETE CASCADE,
    -- CREATED(주문 시작) · STAGE(단계 바뀜) · NOTE(안내) · COMPLETED(수령 완료) · CANCELLED(취소)
    kind        TEXT        NOT NULL CHECK (kind IN ('CREATED', 'STAGE', 'NOTE', 'COMPLETED', 'CANCELLED')),
    -- 이 기록이 남을 때의 단계
    stage       TEXT        NOT NULL CHECK (stage IN
                    ('CONSULTATION', 'MEASUREMENT', 'FABRIC', 'FITTING', 'ADJUSTMENT', 'DELIVERY')),
    message     TEXT        NOT NULL DEFAULT '' CHECK (char_length(message) <= 1000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_bespoke_order_event_order ON bespoke_order_event (order_id, created_at);

COMMENT ON TABLE bespoke_order_event IS '맞춤 주문 진행 기록 — 손님 마이페이지에 그대로 보인다.';
