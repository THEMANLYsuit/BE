-- ============================================================
-- V3 — 원단 · 스타일 카탈로그
--
-- 손님이 "어떤 원단으로 어떤 모양을 만들 수 있는가" 를 둘러보는 목록이다. 가격은 원단마다 "이 원단으로 수트 한 벌" 의
-- 시작가(price_from_krw)만 보여 준다 — 최종 금액은 상담에서 정한다(맞춤이라 옵션 · 품목마다 다르다).
--
-- 사진은 지금 주소(image_url)로만 둔다. 2단계에서 업로드(media)가 붙으면 media_id 로 바꾼다.
-- ============================================================

CREATE TABLE fabric (
    id               UUID        PRIMARY KEY,
    -- 원단 번호 (원단사 번호 그대로). 관리자가 찾는 키이자 손님 화면 주소의 일부.
    code             TEXT        NOT NULL UNIQUE CHECK (code ~ '^[A-Za-z0-9-]{2,40}$'),
    name             TEXT        NOT NULL CHECK (char_length(name) <= 80),
    -- 원단사 (예: VBC, Loro Piana, 제일모직)
    mill             TEXT        NOT NULL DEFAULT '' CHECK (char_length(mill) <= 80),
    -- 혼용률 (예: 울 100%, 울 95% 캐시미어 5%)
    composition      TEXT        NOT NULL DEFAULT '' CHECK (char_length(composition) <= 120),
    -- 중량 (g/m). 없으면 NULL
    weight_gsm       SMALLINT    CHECK (weight_gsm IS NULL OR (weight_gsm BETWEEN 100 AND 800)),
    color            TEXT        NOT NULL DEFAULT '' CHECK (char_length(color) <= 40),
    -- 무늬 (예: 솔리드 · 핀스트라이프 · 글렌체크)
    pattern          TEXT        NOT NULL DEFAULT '' CHECK (char_length(pattern) <= 40),
    -- 계절: ALL(사계절) · SS · FW
    season           TEXT        NOT NULL DEFAULT 'ALL' CHECK (season IN ('ALL', 'SS', 'FW')),
    -- 이 원단으로 만든 투피스 수트의 시작가. NULL 이면 "상담 시 안내"
    price_from_krw   BIGINT      CHECK (price_from_krw IS NULL OR price_from_krw >= 0),
    description      TEXT        NOT NULL DEFAULT '' CHECK (char_length(description) <= 2000),
    image_url        TEXT        CHECK (image_url IS NULL OR image_url ~ '^(https://|/)'),
    -- DRAFT(준비 중) · PUBLISHED(손님에게 보임) · SOLD_OUT(품절 — 보이지만 고를 수 없음)
    status           TEXT        NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'SOLD_OUT')),
    sort_order       INTEGER     NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE fabric IS '원단. 손님 화면에는 PUBLISHED · SOLD_OUT 만 나간다.';

CREATE TRIGGER trg_fabric_updated_at
    BEFORE UPDATE ON fabric
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- 스타일 선택지. 같은 묶음(category) 안에서 하나를 고른다 — 예) LAPEL: 노치 · 피크 · 숄
CREATE TABLE style_option (
    id           UUID        PRIMARY KEY,
    -- 묶음. 값은 StyleCategory enum 과 같다
    category     TEXT        NOT NULL CHECK (category IN (
                     'JACKET_BUTTONS', 'LAPEL', 'VENT', 'POCKET', 'LINING',
                     'TROUSER_PLEAT', 'TROUSER_HEM', 'WAIST', 'MONOGRAM')),
    code         TEXT        NOT NULL CHECK (code ~ '^[a-z0-9-]{2,40}$'),
    name         TEXT        NOT NULL CHECK (char_length(name) <= 60),
    description  TEXT        NOT NULL DEFAULT '' CHECK (char_length(description) <= 500),
    image_url    TEXT        CHECK (image_url IS NULL OR image_url ~ '^(https://|/)'),
    -- 기본값으로 보여 줄 것 (묶음마다 하나)
    is_default   BOOLEAN     NOT NULL DEFAULT FALSE,
    enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order   INTEGER     NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_style_option_code UNIQUE (category, code)
);

-- 묶음마다 기본값은 하나만
CREATE UNIQUE INDEX uq_style_option_default ON style_option (category) WHERE is_default;

CREATE TRIGGER trg_style_option_updated_at
    BEFORE UPDATE ON style_option
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
