-- ============================================================
-- V17 — 사이트 문구를 새 메인에 맞춘다 (2026-11-02 디자이너 시안)
--
-- 메인이 «영상 → Various Body Types → Custom Fit» 로 바뀌어 예전 메인 문구(히어로 · 필름 · 몸 · 하이라이트 ·
-- 가까이 보기 · 선언문)는 쓰지 않는다 — 걷어 낸다. 새 메인 글(story)을 넣는다. 진행 과정 · 예약 · 매장 안내는 그대로.
-- ============================================================

UPDATE site_copy
SET content = (
    (content::jsonb - 'hero' - 'film' - 'body' - 'highlights' - 'closer' - 'manifesto')
    || jsonb_build_object('story', jsonb_build_object(
        'bodiesTitle', 'Various Body Types',
        'fitTitle', 'Custom Fit',
        'fitBody', '몸은 사람마다 다릅니다. 같은 키, 같은 몸무게라도 어깨의 넓이와 가슴의 두께, 허리와 배의 선이 모두 다릅니다. '
                   || '더맨리는 스물네 곳의 치수와 체형 메모로 패턴을 새로 그리고, 가봉에서 몸에 맞춰 고칩니다. '
                   || '마른 몸도, 운동으로 커진 몸도, 넉넉한 몸도 — 그 몸에 맞는 한 벌을 짓습니다.'))
)::text
WHERE id = 1;
