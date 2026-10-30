-- ============================================================
-- V14 — 사이트 문구 (관리자에서 고친다, 2026-10-31)
--
-- 메인(히어로 · 필름 문구 · 01 몸 · 하이라이트 · 가까이 보기 · 선언문 · 마지막 예약) · 진행 과정 여섯 단계 · 매장 안내 글이
-- 지금까지 FE 코드에 있었다. 관리자 › 사이트 문구에서 고칠 수 있게 한 줄짜리 표에 JSON 한 덩어리로 옮긴다.
-- 칸 수(필름 3 · 약속 3 · 하이라이트 5 · 단계 6 · 가까이 보기 5)는 정해져 있다 — 사진 · 연출이 그 수에 맞춰져 있다.
-- 손님 화면은 빌드할 때 받는다 — 저장하면 다시 만든다(FrontRebuildTrigger). 처음 값은 지금 화면 글 그대로다.
-- ============================================================

CREATE TABLE site_copy (
    id          SMALLINT    PRIMARY KEY CHECK (id = 1),
    content     TEXT        NOT NULL CHECK (content IS JSON OBJECT AND char_length(content) <= 20000),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE site_copy IS '사이트 문구 — 한 줄, JSON 한 덩어리(SiteCopy.Content). 손님 화면이 빌드할 때 받는다.';

CREATE TRIGGER trg_site_copy_updated_at
    BEFORE UPDATE ON site_copy
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO site_copy (id, content) VALUES (1, $copy$
{
  "hero": {
    "eyebrow": "BESPOKE TAILORING · SUWON",
    "line1": "당신의 몸에 맞춰",
    "line2": "짓는 한 벌",
    "body": "기성복이 맞지 않는 어깨와 허벅지를 위해, 상담에서 수령까지 여섯 번을 만납니다."
  },
  "film": [
    {
      "eyebrow": "THE PATTERN",
      "title": "선 하나에서 시작합니다",
      "body": "스물네 곳의 치수로 당신만의 패턴을 종이 위에 그립니다."
    },
    {
      "eyebrow": "THE MAKING",
      "title": "몸에 맞춰 지어 갑니다",
      "body": "재단하고, 입혀 보고, 맞지 않는 곳을 고치며 한 벌이 됩니다."
    },
    {
      "eyebrow": "THE SUIT",
      "title": "당신의 이름이 붙은 한 벌",
      "body": "같은 옷이 세상에 하나뿐입니다. 치수는 기록으로 남습니다."
    }
  ],
  "body": {
    "line1": "사진이 아니라",
    "line2": "몸으로 고릅니다",
    "promises": [
      {
        "title": "치수로 시작합니다",
        "body": "스물네 곳의 치수와 체형 메모에서 한 벌이 시작됩니다. 어깨의 기울기, 등의 둥글기처럼 숫자 밖의 것도 적습니다."
      },
      {
        "title": "가봉에서 고칩니다",
        "body": "입어 보고, 서고 앉으며 맞지 않는 곳을 표시합니다. 사진이 아니라 몸으로 확인합니다."
      },
      {
        "title": "기록이 남습니다",
        "body": "치수와 선택은 기록으로 남습니다. 두 번째 한 벌은 상담 한 번으로 시작합니다."
      }
    ]
  },
  "highlights": [
    {
      "title": "스물네 곳을 잽니다",
      "body": "어깨 기울기 · 등의 둥글기처럼 숫자 밖의 것도 기록합니다."
    },
    {
      "title": "원단은 손으로 고릅니다",
      "body": "이탈리아 · 영국 밀의 원단 견본을 직접 만져 봅니다."
    },
    {
      "title": "가봉에서 한 번 더",
      "body": "입어 보고, 서고 앉으며 맞지 않는 곳을 표시합니다."
    },
    {
      "title": "단추 하나까지 고릅니다",
      "body": "라펠 · 벤트 · 단추 · 안감 — 한 벌의 모든 선택을 함께 합니다."
    },
    {
      "title": "다음 한 벌은 더 쉽게",
      "body": "치수와 선택이 기록으로 남아 두 번째는 상담 한 번이면 됩니다."
    }
  ],
  "processIntro": "상담에서 수령까지 여섯 번을 만납니다. 보통 5~6주가 걸립니다.",
  "process": [
    {
      "title": "상담",
      "en": "Consultation",
      "body": "입을 자리와 취향을 듣고, 체형을 봅니다. 원단과 스타일의 방향을 함께 정합니다.",
      "duration": "60분"
    },
    {
      "title": "치수",
      "en": "Measurement",
      "body": "스물네 곳을 잽니다. 어깨의 기울기, 등의 둥글기처럼 숫자 밖의 것도 기록합니다.",
      "duration": "같은 날"
    },
    {
      "title": "원단 · 스타일",
      "en": "Fabric & Style",
      "body": "원단을 손으로 만져 보고 고릅니다. 라펠 · 벤트 · 단추 · 안감까지 한 벌의 모든 선택을 합니다.",
      "duration": "같은 날"
    },
    {
      "title": "가봉",
      "en": "Fitting",
      "body": "가봉 상태의 옷을 입어 봅니다. 서고, 앉고, 팔을 들어 보며 맞지 않는 곳을 표시합니다.",
      "duration": "2 – 3주 뒤"
    },
    {
      "title": "수정 · 완성",
      "en": "Adjustment",
      "body": "가봉에서 표시한 곳을 고쳐 완성합니다. 필요하면 가봉을 한 번 더 합니다.",
      "duration": "2주"
    },
    {
      "title": "수령",
      "en": "Delivery",
      "body": "매장에서 마지막으로 입어 보고 받아 가십니다. 치수는 기록으로 남아 다음 한 벌이 더 쉬워집니다.",
      "duration": "총 5 – 6주"
    }
  ],
  "closer": [
    {
      "title": "라펠",
      "body": "노치 · 피크 · 숄. 얼굴형과 입을 자리에 맞춰 폭과 모양을 고릅니다."
    },
    {
      "title": "어깨",
      "body": "패드의 두께와 어깨선의 각도를 체형에 맞춰 정합니다."
    },
    {
      "title": "단추",
      "body": "뿔 · 자개 · 메탈. 소매 단추는 실제로 열리는 리얼 버튼홀로 만들 수 있습니다."
    },
    {
      "title": "안감",
      "body": "계절과 취향에 따라 전체 · 반 안감, 색과 무늬를 고릅니다."
    },
    {
      "title": "원단",
      "body": "울 · 플란넬 · 리넨. 무게와 짜임으로 계절과 자리를 맞춥니다."
    }
  ],
  "manifesto": "기성복은 평균을 위해 만들어집니다. 당신의 어깨는 평균이 아닙니다. 그래서 우리는 치수를 재고, 가봉에서 고치고, 기록을 남깁니다.",
  "booking": {
    "line1": "첫 상담은",
    "line2": "한 시간이면 충분합니다"
  },
  "shop": {
    "openDays": "월 · 목 · 금 · 토 · 일",
    "hours": "10:00 – 20:00",
    "closedDays": "화 · 수 휴무"
  }
}
$copy$);
