-- ============================================================
-- V5 — 고객 치수 기록
--
-- 매장에서 잰 치수를 회원에게 묶어 둔다. 잴 때마다 새 기록(행)이 생긴다 — 덮어쓰지 않는다.
-- 몸은 바뀌고, "작년 이맘때는 어땠나" 가 다음 한 벌의 근거가 된다. 지금 치수는 가장 최근 기록이다.
--
-- 부위별 값은 measurement_point 에 한 줄씩 둔다(부위 · 값). 부위 이름은 MeasurementPoint enum 과 같다.
-- 탈퇴하면 지운다 — 몸 치수는 주문이 끝난 뒤에 남겨 둘 이유가 없는 개인정보다.
-- ============================================================

CREATE TABLE measurement (
    id           UUID        PRIMARY KEY,
    member_id    UUID        NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    -- 잰 날 (한국 날짜)
    measured_on  DATE        NOT NULL,
    -- 체형 메모 — 어깨 기울기 · 자세 · 좌우 차이 등. 손님에게도 보인다
    body_note    TEXT        NOT NULL DEFAULT '' CHECK (char_length(body_note) <= 1000),
    -- 운영자 메모 — 손님에게 보이지 않는다
    admin_note   TEXT        NOT NULL DEFAULT '' CHECK (char_length(admin_note) <= 1000),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_measurement_member ON measurement (member_id, measured_on DESC, created_at DESC);

COMMENT ON TABLE measurement IS '고객 치수 기록. 잴 때마다 한 행. 탈퇴하면 지운다.';

CREATE TRIGGER trg_measurement_updated_at
    BEFORE UPDATE ON measurement
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- 부위별 값. 단위는 cm, 몸무게(WEIGHT)만 kg. 소수점 한 자리.
CREATE TABLE measurement_point (
    measurement_id  UUID         NOT NULL REFERENCES measurement (id) ON DELETE CASCADE,
    point           TEXT         NOT NULL CHECK (point IN (
                        'HEIGHT', 'WEIGHT',
                        'NECK', 'CHEST', 'WAIST', 'BELLY', 'SHOULDER', 'BACK_WIDTH', 'FRONT_LENGTH', 'BACK_LENGTH',
                        'JACKET_LENGTH', 'SLEEVE_LEFT', 'SLEEVE_RIGHT', 'BICEP', 'WRIST',
                        'HIP', 'TROUSER_WAIST', 'RISE', 'THIGH', 'KNEE', 'CALF', 'HEM', 'OUTSEAM', 'INSEAM')),
    amount          NUMERIC(5,1) NOT NULL CHECK (amount > 0),
    PRIMARY KEY (measurement_id, point)
);

COMMENT ON TABLE measurement_point IS '치수 한 군데. 범위 검사는 서버(MeasurementPoint)가 한다 — 여기는 형식만.';
