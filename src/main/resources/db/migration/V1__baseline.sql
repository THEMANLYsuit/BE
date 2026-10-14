-- ============================================================
-- V1 — 베이스라인 (THE MANLY 맞춤정장 API)
--
-- 어느 도메인에도 속하지 않지만 모든 테이블이 쓸 것만 둔다. 레오네페리토 API 의 V1 과 같다.
-- ============================================================

-- 모든 테이블이 갖게 될 updated_at 을 자동으로 갱신하는 공용 트리거 함수.
-- 관리자 화면이든 배치 SQL 이든 어디서 UPDATE 가 들어와도 updated_at 이 반드시 맞는다.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    -- 실제로 바뀐 게 없으면 updated_at 도 건드리지 않는다.
    IF NEW IS NOT DISTINCT FROM OLD THEN
        RETURN NEW;
    END IF;

    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION set_updated_at() IS
    'BEFORE UPDATE 트리거용. 행이 실제로 변경된 경우에만 updated_at 을 현재 시각으로 갱신한다.';
