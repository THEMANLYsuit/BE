-- ============================================================
-- V15 — 렌탈 (대여)
--
-- 디자이너 방향(10-30): 더맨리 사이트의 기능은 상담 · 문의, 렌탈, 기성복 링크 셋이다. 그중 렌탈.
-- 손님이 옷 · 사이즈 · 수령일 · 반납일을 골라 신청하면 REQUESTED, 관리자가 확인하면 CONFIRMED,
-- 매장에서 건네면 PICKED_UP(대여 중), 돌려받으면 RETURNED. 결제는 매장에서 한다 — 사이트에서는 돈이 오가지 않는다.
--
-- 재고(같은 옷이 몇 벌인가)는 자동으로 세지 않는다 — 관리자가 확인할 때 판단한다. 운영 방식이 정해지면 그때 붙인다.
-- 대여료 · 보증금 · 기간은 옷마다 관리자가 정한다. TODO(고객확인) 실제 금액 · 기간 · 연체 규칙.
-- ============================================================

CREATE TABLE rental_item (
    id            UUID        PRIMARY KEY,
    name          TEXT        NOT NULL CHECK (char_length(name) <= 80),
    -- 묶음 (예: 수트 · 턱시도 · 조끼). 손님 화면에서 나눠 보여 줄 때 쓴다
    category      TEXT        NOT NULL DEFAULT '' CHECK (char_length(category) <= 40),
    description   TEXT        NOT NULL DEFAULT '' CHECK (char_length(description) <= 2000),
    image_url     TEXT        CHECK (image_url IS NULL OR image_url ~ '^(https://|/)'),
    -- 대여료 (한 번, 아래 기간까지). NULL 이면 "상담 시 안내"
    price_krw     BIGINT      CHECK (price_krw IS NULL OR price_krw >= 0),
    -- 보증금. NULL 이면 없음
    deposit_krw   BIGINT      CHECK (deposit_krw IS NULL OR deposit_krw >= 0),
    -- 한 번에 빌릴 수 있는 날 수 — 수령일부터 반납일까지
    rental_days   SMALLINT    NOT NULL DEFAULT 3 CHECK (rental_days BETWEEN 1 AND 30),
    -- 고를 수 있는 사이즈, 쉼표로 (예: 46,48,50). 비어 있으면 사이즈를 묻지 않는다(상담 때 맞춘다)
    sizes         TEXT        NOT NULL DEFAULT '' CHECK (char_length(sizes) <= 200),
    -- DRAFT(준비 중) · PUBLISHED(신청 받음) · UNAVAILABLE(보이지만 신청 못 함 — 수선 · 분실 등)
    status        TEXT        NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'UNAVAILABLE')),
    sort_order    INTEGER     NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE rental_item IS '대여 옷. 손님 화면에는 PUBLISHED · UNAVAILABLE 만 나간다.';

CREATE TRIGGER trg_rental_item_updated_at
    BEFORE UPDATE ON rental_item
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


CREATE TABLE rental (
    id                UUID        PRIMARY KEY,
    member_id         UUID        NOT NULL REFERENCES member (id),
    item_id           UUID        NOT NULL REFERENCES rental_item (id),
    -- 신청 때의 옷 이름 · 대여료 — 나중에 옷 정보를 고쳐도 이 기록은 그대로다
    item_name         TEXT        NOT NULL CHECK (char_length(item_name) <= 80),
    price_krw         BIGINT      CHECK (price_krw IS NULL OR price_krw >= 0),
    size_label        TEXT        NOT NULL DEFAULT '' CHECK (char_length(size_label) <= 20),
    pickup_date       DATE        NOT NULL,
    return_date       DATE        NOT NULL,
    -- REQUESTED(신청) · CONFIRMED(확정) · PICKED_UP(대여 중) · RETURNED(반납) · CANCELLED(취소)
    status            TEXT        NOT NULL DEFAULT 'REQUESTED'
                      CHECK (status IN ('REQUESTED', 'CONFIRMED', 'PICKED_UP', 'RETURNED', 'CANCELLED')),
    -- 손님 메모 (입을 자리 · 키 · 몸무게 등)
    customer_note     TEXT        NOT NULL DEFAULT '' CHECK (char_length(customer_note) <= 1000),
    -- 운영자 메모 (손님 화면에 보이지 않는다)
    admin_note        TEXT        NOT NULL DEFAULT '' CHECK (char_length(admin_note) <= 1000),
    cancelled_by      TEXT        CHECK (cancelled_by IS NULL OR cancelled_by IN ('CUSTOMER', 'ADMIN')),
    cancel_reason     TEXT        CHECK (cancel_reason IS NULL OR char_length(cancel_reason) <= 300),
    confirmed_at      TIMESTAMPTZ,
    picked_up_at      TIMESTAMPTZ,
    returned_at       TIMESTAMPTZ,
    cancelled_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT rental_dates_in_order CHECK (return_date >= pickup_date)
);

CREATE INDEX ix_rental_member ON rental (member_id, pickup_date DESC);
CREATE INDEX ix_rental_pickup ON rental (pickup_date);
CREATE INDEX ix_rental_item ON rental (item_id, pickup_date);

COMMENT ON TABLE rental IS '대여 신청. 결제는 매장에서. 지우지 않는다 — 손님 이력이다.';

CREATE TRIGGER trg_rental_updated_at
    BEFORE UPDATE ON rental
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
