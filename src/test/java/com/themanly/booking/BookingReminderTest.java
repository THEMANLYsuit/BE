package com.themanly.booking;

import static com.themanly.TestSupport.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.themanly.TestMailConfiguration;
import com.themanly.TestSupport;
import com.themanly.TestcontainersConfiguration;
import com.themanly.auth.AuthService;
import com.themanly.member.AdminMemberService;
import com.themanly.member.MemberRole;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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

/** 예약 전날 알림(V12) — 다음 날 확정 예약에만 한 번, 신청 · 취소 예약은 안 보내고, 시간을 옮기면 다시 보낸다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
// 테스트 중에 정해진 시각 작업이 끼어들지 않게 끈다 — 직접 부른다
@TestPropertySource(properties = "app.booking.reminder-cron=-")
class BookingReminderTest {

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
    private BookingReminder reminder;
    @Autowired
    private TestMailConfiguration.RecordingMailer mailer;

    private Cookie hong;
    private Cookie kim;
    private Cookie admin;
    private LocalDate day;

    @BeforeEach
    void setUp() throws Exception {
        TestSupport.cleanDatabase(jdbc);
        hong = TestSupport.member(mockMvc, auth, HONG, "홍길동");
        kim = TestSupport.member(mockMvc, auth, KIM, "김철수");
        admins.createWithLoginId("opsadmin", "ops@example.com", "운영자", MemberRole.ADMIN, TestSupport.PASSWORD);
        admin = TestSupport.adminLogin(mockMvc, "opsadmin");
        jdbc.update("UPDATE member SET must_change_password = FALSE WHERE email = 'ops@example.com'");
        day = LocalDate.now(BookingPolicy.SEOUL).plusDays(3);
        while (policy.slotsOn(day).size() < 3) {
            day = day.plusDays(1);
        }
        mailer.clear();
    }

    /** 규칙에 맞는 자리에 신청하고 id 를 돌려준다 */
    private String book(Cookie who, Instant slot) throws Exception {
        String res = mockMvc.perform(csrf(mockMvc, post("/api/bookings"), who).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"CONSULTATION\",\"startsAt\":\"%s\",\"note\":\"\"}".formatted(slot)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.id");
    }

    private void confirm(String id) throws Exception {
        mockMvc.perform(csrf(mockMvc, post("/api/admin/bookings/" + id + "/confirm"), admin)).andExpect(status().isOk());
    }

    private long reminders(String to) {
        return mailer.to(to).stream().filter(m -> m.subject().contains("내일")).count();
    }

    @Test
    @DisplayName("그날 확정 예약에만 한 번 보낸다 — 신청(미확정)은 안 보내고, 다시 돌려도 두 번 안 간다")
    void remindsConfirmedOnce() throws Exception {
        String a = book(hong, policy.slotsOn(day).get(0));
        book(kim, policy.slotsOn(day).get(1)); // 신청만
        confirm(a);
        mailer.clear();

        assertThat(reminder.remindFor(day)).isEqualTo(1);
        assertThat(reminders(HONG)).isEqualTo(1);
        assertThat(reminders(KIM)).isZero();
        assertThat(mailer.to(HONG).getFirst().text()).contains("THE MANLY 수원 매장");

        assertThat(reminder.remindFor(day)).isZero();
        assertThat(reminders(HONG)).isEqualTo(1);
        // 다른 날은 대상이 아니다
        assertThat(reminder.remindFor(day.plusDays(1))).isZero();
    }

    @Test
    @DisplayName("관리자가 다른 날로 옮기면 표시가 지워져 새 날짜 전날에 다시 보낸다. 취소한 예약은 보내지 않는다")
    void rescheduleRemindsAgain() throws Exception {
        String a = book(hong, policy.slotsOn(day).get(0));
        confirm(a);
        assertThat(reminder.remindFor(day)).isEqualTo(1);

        LocalDate next = day.plusDays(1);
        while (policy.slotsOn(next).isEmpty()) {
            next = next.plusDays(1);
        }
        mockMvc.perform(csrf(mockMvc, put("/api/admin/bookings/" + a + "/schedule"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startsAt\":\"%s\"}".formatted(policy.slotsOn(next).get(0))))
                .andExpect(status().isOk());
        assertThat(reminder.remindFor(next)).isEqualTo(1);

        String b = book(kim, policy.slotsOn(day).get(2));
        confirm(b);
        mockMvc.perform(csrf(mockMvc, post("/api/admin/bookings/" + b + "/cancel"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"매장 사정\"}")).andExpect(status().isOk());
        assertThat(reminder.remindFor(day)).isZero();
    }

    @Test
    @DisplayName("하루의 경계는 서울 시각 — 그날 0시 이상 다음 날 0시 미만")
    void dayBoundaryInSeoul() throws Exception {
        String a = book(hong, policy.slotsOn(day).get(0));
        confirm(a);
        // 다음 날 0시 정각으로 옮겨 두면 그날이 아니라 다음 날 대상이다
        Instant midnight = day.plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(BookingPolicy.SEOUL).toInstant();
        jdbc.update("UPDATE appointment SET starts_at = ? WHERE id = ?::uuid", Timestamp.from(midnight), a);
        assertThat(reminder.remindFor(day)).isZero();
        assertThat(reminder.remindFor(day.plusDays(1))).isEqualTo(1);
    }
}
