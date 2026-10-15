# THE MANLY 맞춤정장 API

더맨리 맞춤정장 사이트의 백엔드. Spring Boot 4.1 · Java 21 · PostgreSQL 18 — 레오네페리토 API 와 같은 틀이다.

기성복 사이트와 다른 점은 **파는 방식**이다. 장바구니 · 결제 대신:

1. **상담 · 가봉 예약** (`/api/bookings`) — 손님이 빈 시간을 골라 신청, 관리자가 확정. 메일 알림.
2. **고객 치수 기록** — 2단계
3. **맞춤 주문 진행 단계** (상담 → 원단 선택 → 가봉 → 수정 → 완성 → 수령) — 2단계
4. **원단 · 스타일 카탈로그** (`/api/fabrics` · `/api/style-options`) — 관리자가 등록, 손님이 둘러본다.

회원 · 로그인 · 관리자 · 세션 · 비밀번호 찾기는 레오네페리토와 같다 (`/api/auth`, `/api/me`, `/api/admin/members`).
간편 로그인(카카오 · 네이버)은 아직 없다.

## 로컬 실행

레오네페리토를 같이 띄워도 겹치지 않게 포트가 다르다: API **8081** · DB **5434** · 메일 **1026 / 8026** · FE **3001**.

```bash
docker compose up -d           # PostgreSQL 18 + Mailpit
./gradlew bootRun              # 기본 프로필 local, http://localhost:8081
./gradlew test                 # Testcontainers 가 PostgreSQL 을 띄운다 (Docker 필요)
```

메일은 실제로 나가지 않고 Mailpit 에 쌓인다: http://localhost:8026

관리자 만들기 (로컬 — 아이디 + 임시 비밀번호. 첫 로그인 뒤 비밀번호를 바꿔야 관리자 API 가 열린다):

```bash
./gradlew bootRun --args="create-admin --login-id=masteradmin --temp-password=바꿀비밀번호10자이상 --role=SUPER_ADMIN"
```

운영에서는 `--temp-password` 없이 실행하면 비밀번호 설정 링크가 나온다.

## Java 파일 (46개 · `src/main/java/com/themanly/`)

1단계 46개가 모두 들어 있다(2026-10-14). 패키지끼리 서로 참조하므로 하나라도 빠지면 컴파일되지 않는다. `./gradlew test` — 12개.

| 패키지 | 파일 |
|---|---|
| (루트) | ThemanlyApiApplication |
| common/error | ErrorResponse · ResourceNotFoundException · DomainRuleException · GlobalExceptionHandler |
| common/log | RequestLoggingFilter |
| mail | Mailer · MailConfig |
| member | MemberRole · MemberStatus · Member · MemberRepository · CreateAdminCommand · AdminMemberService · MemberAccountService |
| member/api | MeController · AdminMemberController |
| auth | MemberPrincipal · AuthenticationFailedException · PasswordPolicy · LoginAttemptRecorder · SessionTerminator · AuthService · PasswordResetToken · PasswordResetTokenRepository · PasswordResetService · AdminPasswordGate · SecurityConfig |
| auth/api | AuthRequests · AuthController |
| catalog | Fabric · FabricRepository · StyleOption · StyleOptionRepository · CatalogService |
| catalog/api | CatalogController · AdminCatalogController |
| booking | AppointmentType · AppointmentStatus · Appointment · AppointmentRepository · BookingPolicy · BookingService · BookingNotifier |
| booking/api | BookingController · AdminBookingController |

## 예약 규칙 (application.yml `app.booking`)

| 값 | 기본 | 뜻 |
|---|---|---|
| open-hour / close-hour | 10 / 20 | 영업 시간. 자리는 이 안에서 `slot-minutes` 단위 |
| slot-minutes | 60 | 한 자리 길이. 상담 · 가봉 60분, 수령 30분이지만 자리는 이 단위로 자른다 |
| closed-days | TUESDAY,WEDNESDAY | 휴무 요일 — 자리가 없다 |
| min-lead-hours | 24 | 지금부터 이만큼 뒤부터 받는다 |
| max-ahead-days | 60 | 이만큼 앞까지 받는다 |

같은 시각의 살아 있는(신청 · 확정) 예약은 하나다. 손님 한 명은 살아 있는 예약을 3개까지만 둔다.
새 예약 · 손님 취소는 `BOOKING_NOTIFY_EMAILS` 의 관리자에게, 확정 · 시간 변경 · 관리자 취소는 손님에게 메일이 간다.

## 배포

레오네페리토와 같은 방식(Lightsail 한 대 · Docker · Caddy)으로 올린다. 운영 구성(`docker-compose.prod.yml` · `deploy/`)은
도메인이 정해진 뒤에 레오네페리토의 것을 옮겨 만든다.
