-- ============================================================
-- V2 — 회원 + 세션 저장소
--
-- 레오네페리토 API 의 V4 · V5 · V10 · V12 · V15 를 처음부터 하나로 합쳤다(같은 규칙, 새 사이트라 이력이 없다).
-- 간편가입(카카오 · 네이버)은 없다 — 맞춤정장 손님은 상담 예약으로 들어오고, 필요하면 나중에 V 를 더한다.
-- ============================================================

CREATE TABLE member (
    id                    UUID        PRIMARY KEY,

    -- 이메일이 곧 로그인 아이디다. 소문자로 정규화해서 저장하고 DB 가 그걸 강제한다.
    email                 TEXT        NOT NULL UNIQUE
                          CHECK (email = lower(email) AND email LIKE '%@%'),

    -- BCrypt 해시. 평문 비밀번호는 어디에도 저장하지 않는다.
    password_hash         TEXT        NOT NULL,

    name                  TEXT        NOT NULL,
    -- 예약 확인 전화용. 가입 때는 안 받을 수 있다.
    phone                 TEXT,

    -- WITHDRAWN 은 행을 지우는 대신 남긴다 — 예약 · 주문 이력이 회원을 참조한다. 개인정보는 비운다.
    status                TEXT        NOT NULL DEFAULT 'ACTIVE'
                          CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),

    -- MEMBER(손님) · ADMIN(운영) · SUPER_ADMIN(관리자 지정 — 서버 명령으로만 만든다)
    role                  TEXT        NOT NULL DEFAULT 'MEMBER'
                          CHECK (role IN ('MEMBER', 'ADMIN', 'SUPER_ADMIN')),

    -- 관리자 아이디 로그인용. 서버 명령(create-admin)으로만 붙는다.
    login_id              TEXT        UNIQUE CHECK (login_id IS NULL OR login_id ~ '^[a-z0-9_]{4,30}$'),
    -- 임시 비밀번호 계정. 바꾸기 전에는 관리자 API 가 막힌다.
    must_change_password  BOOLEAN     NOT NULL DEFAULT FALSE,

    -- 로그인 실패 누적과 잠금 해제 시각. 무차별 대입을 늦춘다.
    failed_login_attempts SMALLINT    NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,

    -- 약관 동의 · 만 14세 확인
    terms_agreed_at       TIMESTAMPTZ,
    terms_version         TEXT,

    withdrawn_at          TIMESTAMPTZ,

    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE  member IS '회원. 이메일이 로그인 아이디다. 관리자는 login_id 로도 들어온다.';
COMMENT ON COLUMN member.password_hash IS 'BCrypt 해시. 평문은 저장하지 않는다.';
COMMENT ON COLUMN member.status IS 'WITHDRAWN 은 로그인에서 없는 계정과 동일하게 취급한다.';

CREATE TRIGGER trg_member_updated_at
    BEFORE UPDATE ON member
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ─────────────────────────────────────────────────────────────
-- 세션 저장소 (Spring Session JDBC) — spring-session-jdbc 의 schema-postgresql.sql 을 그대로 옮긴 것.
-- 이름 · 타입을 고치면 Spring Session 이 못 읽는다. 손대지 않는다.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE SPRING_SESSION (
	PRIMARY_ID CHAR(36) NOT NULL,
	SESSION_ID CHAR(36) NOT NULL,
	CREATION_TIME BIGINT NOT NULL,
	LAST_ACCESS_TIME BIGINT NOT NULL,
	MAX_INACTIVE_INTERVAL INT NOT NULL,
	EXPIRY_TIME BIGINT NOT NULL,
	PRINCIPAL_NAME VARCHAR(100),
	CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
	SESSION_PRIMARY_ID CHAR(36) NOT NULL,
	ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
	ATTRIBUTE_BYTES BYTEA NOT NULL,
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
	CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);


-- ─────────────────────────────────────────────────────────────
-- 비밀번호 설정 · 재설정 링크 (관리자 생성 명령 · 비밀번호 찾기). 30분, 한 번.
-- 토큰은 해시로만 저장한다 — DB 가 새도 링크를 만들 수 없다.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE password_reset_token (
    id          UUID        PRIMARY KEY,
    member_id   UUID        NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    token_hash  TEXT        NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_password_reset_token_member ON password_reset_token (member_id);
