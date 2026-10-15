package com.themanly.booking;

import static com.themanly.TestSupport.PASSWORD;
import static com.themanly.TestSupport.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.themanly.TestMailConfiguration;
import com.themanly.TestSupport;
import com.themanly.TestcontainersConfiguration;
import com.themanly.auth.AuthService;
import com.themanly.member.AdminMemberService;
import com.themanly.member.MemberRole;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 상담 · 가봉 예약 — 빈 시간, 신청 규칙, 같은 자리 경쟁, 손님 취소, 관리자 확정 · 메일, 탈퇴 제한. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
@TestPropertySource(properties = "app.notify.admin-emails=ops@example.com")
class BookingApiTest {

    private static final String HONG = "hong@example.com";
    private static final String KIM = "kim@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuthService auth;
    @Autowired
    private AdminMemberService admins;
    @Autowired
    private BookingPolicy policy;
    @Autowired
    private TestMailConfiguration.RecordingMailer mailer;

    private Cookie hong;
    private Cookie kim;
    private Cookie admin;
    /** 영업일의 두 번째 자리 — 규칙(24시간 뒤 · 60일 안)에 맞는 시각 */
    private Instant slot;
    private LocalDate day;

    @BeforeEach
    void setUp() throws Exception {
        TestSupport.cleanDatabase(jdbc);
        mailer.clear();
        hong = TestSupport.member(mockMvc, auth, HONG, "홍길동");
        kim = TestSupport.member(mockMvc, auth, KIM, "김철수");
        admins.createWithLoginId("opsadmin", "ops@example.com", "운영자", MemberRole.ADMIN, PASSWORD);
        admin = TestSupport.adminLogin(mockMvc, "opsadmin");
        jdbc.update("UPDATE member SET must_change_password = FALSE WHERE email = 'ops@example.com'");
        day = LocalDate.now(BookingPolicy.SEOUL).plusDays(3);
        while (policy.slotsOn(day).isEmpty()) {
            day = day.plusDays(1);
        }
        slot = policy.slotsOn(day).get(1);
    }

    private String body(Instant startsAt) {
        return "{\"type\":\"CONSULTATION\",\"startsAt\":\"%s\",\"note\":\"결혼식 수트\"}".formatted(startsAt);
    }

    @Test
    @DisplayName("빈 시간 — 로그인 없이 본다. 휴무일은 자리가 없고, 예약된 자리는 available=false 다")
    void availability() throws Exception {
        mockMvc.perform(csrf(mockMvc, post("/api/bookings"), hong).contentType(MediaType.APPLICATION_JSON).content(body(slot)))
                .andExpect(status().isCreated());
        String res = mockMvc.perform(get("/api/bookings/availability").param("from", day.toString()).param("to", day.plusDays(6).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andReturn().getResponse().getContentAsString();
        List<Boolean> closed = JsonPath.read(res, "$[*].closed");
        assertThat(closed).contains(true); // 7일 안에 화 · 수 휴무가 있다
        List<Boolean> available = JsonPath.read(res, "$[0].slots[*].available");
        assertThat(available.get(1)).isFalse();
        assertThat(available.get(0)).isTrue();
        List<List<Object>> closedSlots = JsonPath.read(res, "$[?(@.closed == true)].slots");
        assertThat(closedSlots).allMatch(List::isEmpty);

        mockMvc.perform(get("/api/bookings/availability").param("from", day.toString()).param("to", day.plusDays(40).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("신청 — 로그인 필요. 같은 자리는 한 명만(409). 휴무일 · 24시간 안 · 눈금 밖은 400. 손님은 3개까지")
    void requestRules() throws Exception {
        mockMvc.perform(csrf(mockMvc, post("/api/bookings")).contentType(MediaType.APPLICATION_JSON).content(body(slot)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(csrf(mockMvc, post("/api/bookings"), hong).contentType(MediaType.APPLICATION_JSON).content(body(slot)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.typeLabel").value("첫 상담"))
                .andExpect(jsonPath("$.durationMinutes").value(60));
        mockMvc.perform(csrf(mockMvc, post("/api/bookings"), kim).contentType(MediaType.APPLICATION_JSON).content(body(slot)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLOT_TAKEN"));

        mockMvc.perform(csrf(mockMvc, post("/api/bookings"), kim).contentType(MediaType.APPLICATION_JSON)
                        .content(body(slot.plusSeconds(15 * 60)))) // 눈금 밖
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_BOOKABLE"));
        mockMvc.perform(csrf(mockMvc, post("/api/bookings"), kim).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Instant.now().plusSeconds(3600)))) // 24시간 안
                .andExpect(status().isBadRequest());
        LocalDate closedDay = day;
        while (!policy.isClosed(closedDay)) {
            closedDay = closedDay.plusDays(1);
        }
        mockMvc.perform(csrf(mockMvc, post("/api/bookings"), kim).contentType(MediaType.APPLICATION_JSON)
                        .content(body(closedDay.atTime(11, 0).atZone(BookingPolicy.SEOUL).toInstant())))
                .andExpect(status().isBadRequest());

        // 손님 한 명은 살아 있는 예약 3개까지
        List<Instant> slots = policy.slotsOn(day);
        for (int i = 2; i < 4; i++) {
            mockMvc.perform(csrf(mockMvc, post("/api/bookings"), hong).contentType(MediaType.APPLICATION_JSON).content(body(slots.get(i))))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(csrf(mockMvc, post("/api/bookings"), hong).contentType(MediaType.APPLICATION_JSON).content(body(slots.get(4))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOO_MANY_LIVE"));

        // 메일: 손님에게 접수, 관리자에게 신청
        assertThat(mailer.to(HONG)).isNotEmpty();
        assertThat(mailer.to("ops@example.com")).anySatisfy(m -> assertThat(m.subject()).startsWith("[예약 신청]"));
    }

    @Test
    @DisplayName("관리자 확정 → 손님에게 확정 메일. 손님 취소 → 관리자에게 취소 메일, 자리가 비고, 남의 예약은 404")
    void confirmAndCancel() throws Exception {
        String res = mockMvc.perform(csrf(mockMvc, post("/api/bookings"), hong).contentType(MediaType.APPLICATION_JSON).content(body(slot)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(res, "$.id");

        mockMvc.perform(get("/api/admin/bookings").cookie(admin).param("from", day.toString()).param("to", day.toString()))
                .andExpect(jsonPath("$[0].customer.name").value("홍길동"))
                .andExpect(jsonPath("$[0].customer.email").value(HONG));
        mockMvc.perform(csrf(mockMvc, post("/api/admin/bookings/" + id + "/confirm"), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(mailer.to(HONG)).anySatisfy(m -> assertThat(m.subject()).contains("확정"));
        mockMvc.perform(csrf(mockMvc, post("/api/admin/bookings/" + id + "/confirm"), admin))
                .andExpect(status().isConflict()); // 두 번 확정은 안 된다

        mockMvc.perform(csrf(mockMvc, post("/api/bookings/" + id + "/cancel"), kim)).andExpect(status().isNotFound());
        mockMvc.perform(csrf(mockMvc, post("/api/bookings/" + id + "/cancel"), hong))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(mailer.to("ops@example.com")).anySatisfy(m -> assertThat(m.subject()).startsWith("[예약 취소]"));
        mockMvc.perform(csrf(mockMvc, post("/api/bookings"), kim).contentType(MediaType.APPLICATION_JSON).content(body(slot)))
                .andExpect(status().isCreated()); // 자리가 비었다
        mockMvc.perform(get("/api/bookings/mine").cookie(hong))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].status").value("CANCELLED"));
    }

    @Test
    @DisplayName("가봉 예약을 주문에 묶는다 — 내 진행 중 주문만. 관리자는 바로 확정 예약을 만들고, 주문 상세 · 회원 상세 · 대시보드에 보인다")
    void orderLinkedAndAdminCreated() throws Exception {
        String hongId = jdbc.queryForObject("SELECT id::text FROM member WHERE email = ?", String.class, HONG);
        String no = JsonPath.read(mockMvc.perform(csrf(mockMvc, post("/api/admin/members/" + hongId + "/orders"), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"garment\":\"SUIT_2PC\",\"title\":\"네이비 수트\",\"styles\":{},\"message\":\"\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.order.summary.orderNumber");

        // 남의 주문(김철수) · 없는 주문번호 → 400
        mockMvc.perform(csrf(mockMvc, post("/api/bookings"), kim).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FITTING\",\"startsAt\":\"%s\",\"orderNumber\":\"%s\"}".formatted(slot, no)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_ORDER"));
        // 내 주문 → 묶인다
        mockMvc.perform(csrf(mockMvc, post("/api/bookings"), hong).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FITTING\",\"startsAt\":\"%s\",\"orderNumber\":\"%s\"}".formatted(slot, no)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.orderNumber").value(no));
        mockMvc.perform(get("/api/bookings/mine").cookie(hong)).andExpect(jsonPath("$[0].orderNumber").value(no));

        // 관리자가 수령 예약을 바로 확정으로 — 24시간 제한 없이, 빈 자리면 된다
        Instant other = policy.slotsOn(day).get(3);
        mockMvc.perform(csrf(mockMvc, post("/api/admin/members/" + hongId + "/bookings"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PICKUP\",\"startsAt\":\"%s\",\"adminNote\":\"완성 뒤 수령\",\"orderNumber\":\"%s\"}".formatted(other, no)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.orderNumber").value(no))
                .andExpect(jsonPath("$.customer.name").value("홍길동"));
        assertThat(mailer.to(HONG)).anySatisfy(m -> assertThat(m.subject()).contains("수령 예약이 확정"));
        mockMvc.perform(csrf(mockMvc, post("/api/admin/members/" + hongId + "/bookings"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PICKUP\",\"startsAt\":\"%s\"}".formatted(other)))
                .andExpect(status().isConflict()); // 같은 자리

        mockMvc.perform(get("/api/admin/orders/" + no).cookie(admin))
                .andExpect(jsonPath("$.bookings.length()").value(2))
                .andExpect(jsonPath("$.bookings[0].type").value("FITTING"))
                .andExpect(jsonPath("$.bookings[1].status").value("CONFIRMED"));
        mockMvc.perform(get("/api/admin/members/" + hongId + "/overview").cookie(admin))
                .andExpect(jsonPath("$.member.name").value("홍길동"))
                .andExpect(jsonPath("$.latestMeasurement").isEmpty())
                .andExpect(jsonPath("$.orders.length()").value(1))
                .andExpect(jsonPath("$.upcoming.length()").value(2));
        mockMvc.perform(get("/api/admin/dashboard").cookie(admin))
                .andExpect(jsonPath("$.requestedAppointments").value(1))
                .andExpect(jsonPath("$.inProgressOrders").value(1))
                .andExpect(jsonPath("$.ordersByStage.CONSULTATION").value(1))
                .andExpect(jsonPath("$.ordersByStage.FITTING").value(0));
        mockMvc.perform(get("/api/admin/dashboard").cookie(hong)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자 시간 옮기기 — 신청 상태면 옮긴 김에 확정, 빈 자리여야 한다. 예약이 살아 있으면 탈퇴할 수 없다")
    void rescheduleAndWithdraw() throws Exception {
        String res = mockMvc.perform(csrf(mockMvc, post("/api/bookings"), hong).contentType(MediaType.APPLICATION_JSON).content(body(slot)))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(res, "$.id");
        Instant other = policy.slotsOn(day).get(3);
        mockMvc.perform(csrf(mockMvc, put("/api/admin/bookings/" + id + "/schedule"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startsAt\":\"%s\"}".formatted(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.startsAt").value(other.toString()));
        mockMvc.perform(csrf(mockMvc, put("/api/admin/bookings/" + id + "/note"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminNote\":\"네이비 원단 희망\"}"))
                .andExpect(jsonPath("$.adminNote").value("네이비 원단 희망"));
        mockMvc.perform(get("/api/bookings/mine").cookie(hong)).andExpect(jsonPath("$[0].note").value("결혼식 수트"));

        mockMvc.perform(csrf(mockMvc, post("/api/me/withdraw"), hong).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("APPOINTMENTS_IN_PROGRESS"));
        mockMvc.perform(csrf(mockMvc, post("/api/admin/bookings/" + id + "/complete"), admin))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(csrf(mockMvc, post("/api/me/withdraw"), hong).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isNoContent());
    }
}
