package com.themanly.rental;

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
import com.themanly.booking.BookingPolicy;
import com.themanly.member.AdminMemberService;
import com.themanly.member.MemberRole;
import com.themanly.site.FrontRebuildTrigger;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

/** 렌탈 — 대여 옷 노출 규칙, 신청 날짜 · 사이즈 규칙, 손님 취소, 관리자 확정 → 건넴 → 반납, 메일, 탈퇴 제한. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
@TestPropertySource(properties = "app.notify.admin-emails=ops@example.com")
@RecordApplicationEvents
class RentalApiTest {

    private static final String HONG = "hong@example.com";

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
    @Autowired
    private ApplicationEvents events;

    private Cookie hong;
    private Cookie admin;
    /** 규칙(2일 뒤 · 60일 안)에 맞는 영업일 */
    private LocalDate pickup;

    @BeforeEach
    void setUp() throws Exception {
        TestSupport.cleanDatabase(jdbc);
        mailer.clear();
        hong = TestSupport.member(mockMvc, auth, HONG, "홍길동");
        admins.createWithLoginId("opsadmin", "ops@example.com", "운영자", MemberRole.ADMIN, PASSWORD);
        jdbc.update("UPDATE member SET must_change_password = FALSE WHERE email = 'ops@example.com'");
        admin = TestSupport.adminLogin(mockMvc, "opsadmin");
        pickup = openDayFrom(LocalDate.now(BookingPolicy.SEOUL).plusDays(5));
    }

    private LocalDate openDayFrom(LocalDate d) {
        while (policy.isClosed(d)) {
            d = d.plusDays(1);
        }
        return d;
    }

    private String createItem(String status) throws Exception {
        String res = mockMvc.perform(csrf(mockMvc, post("/api/admin/rental-items"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"블랙 턱시도","category":"턱시도","description":"피크 라펠.","imageUrl":"",
                                 "priceKrw":150000,"depositKrw":100000,"rentalDays":3,"sizes":["46"," 48","50","48"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.sizes.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(res, "$.id");
        if (status != null) {
            mockMvc.perform(csrf(mockMvc, put("/api/admin/rental-items/" + id + "/status"), admin).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"%s\"}".formatted(status))).andExpect(status().isOk());
        }
        return id;
    }

    private String request(String itemId, String size, LocalDate from, LocalDate to) {
        return "{\"itemId\":\"%s\",\"size\":%s,\"pickupDate\":\"%s\",\"returnDate\":\"%s\",\"note\":\"결혼식 하객\"}"
                .formatted(itemId, size == null ? "null" : "\"" + size + "\"", from, to);
    }

    @Test
    @DisplayName("대여 옷 — 준비 중은 손님에게 안 보이고(404), 공개 · 신청 불가는 보인다. 보이는 것이 바뀌면 화면을 다시 만든다")
    void items() throws Exception {
        String id = createItem(null);
        mockMvc.perform(get("/api/rental-items")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/rental-items/" + id)).andExpect(status().isNotFound());
        assertThat(events.stream(FrontRebuildTrigger.SiteChanged.class).count()).isZero();

        mockMvc.perform(csrf(mockMvc, put("/api/admin/rental-items/" + id + "/status"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"UNAVAILABLE\"}")).andExpect(status().isOk());
        mockMvc.perform(get("/api/rental-items/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rentable").value(false))
                .andExpect(jsonPath("$.priceKrw").value(150000))
                .andExpect(jsonPath("$.sizes[1]").value("48"));
        assertThat(events.stream(FrontRebuildTrigger.SiteChanged.class).count()).isEqualTo(1);

        // 신청 불가인 옷은 신청도 막힌다
        mockMvc.perform(csrf(mockMvc, post("/api/rentals"), hong).contentType(MediaType.APPLICATION_JSON)
                        .content(request(id, "48", pickup, pickup.plusDays(1))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_RENTABLE"));
    }

    @Test
    @DisplayName("신청 — 로그인 필요. 사이즈는 목록 안, 수령일 2일 뒤부터, 기간은 옷의 날 수 안, 휴무일 안 됨. 손님은 3건까지")
    void requestRules() throws Exception {
        String id = createItem("PUBLISHED");
        mockMvc.perform(csrf(mockMvc, post("/api/rentals")).contentType(MediaType.APPLICATION_JSON)
                        .content(request(id, "48", pickup, pickup.plusDays(1))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(csrf(mockMvc, post("/api/rentals"), hong).contentType(MediaType.APPLICATION_JSON)
                        .content(request(id, "52", pickup, pickup.plusDays(1))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_SIZE"));
        mockMvc.perform(csrf(mockMvc, post("/api/rentals"), hong).contentType(MediaType.APPLICATION_JSON)
                        .content(request(id, "48", LocalDate.now(BookingPolicy.SEOUL).plusDays(1), pickup)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATES"));
        mockMvc.perform(csrf(mockMvc, post("/api/rentals"), hong).contentType(MediaType.APPLICATION_JSON)
                        .content(request(id, "48", pickup, pickup.plusDays(10)))) // 3일 넘음
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATES"));
        mockMvc.perform(csrf(mockMvc, post("/api/rentals"), hong).contentType(MediaType.APPLICATION_JSON)
                        .content(request(id, "48", pickup, pickup.minusDays(1))))
                .andExpect(status().isBadRequest());
        LocalDate closed = pickup;
        while (!policy.isClosed(closed)) {
            closed = closed.plusDays(1);
        }
        mockMvc.perform(csrf(mockMvc, post("/api/rentals"), hong).contentType(MediaType.APPLICATION_JSON)
                        .content(request(id, "48", closed, closed)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATES"));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(csrf(mockMvc, post("/api/rentals"), hong).contentType(MediaType.APPLICATION_JSON)
                            .content(request(id, "48", pickup, pickup)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("REQUESTED"))
                    .andExpect(jsonPath("$.itemName").value("블랙 턱시도"));
        }
        mockMvc.perform(csrf(mockMvc, post("/api/rentals"), hong).contentType(MediaType.APPLICATION_JSON)
                        .content(request(id, "48", pickup, pickup)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOO_MANY_LIVE"));
    }

    @Test
    @DisplayName("흐름 — 신청 메일(손님 · 관리자) → 확정 메일 → 건넴 → 반납. 대여 중에는 취소 · 탈퇴가 안 되고, 반납하면 탈퇴된다")
    void flow() throws Exception {
        String itemId = createItem("PUBLISHED");
        String res = mockMvc.perform(csrf(mockMvc, post("/api/rentals"), hong).contentType(MediaType.APPLICATION_JSON)
                        .content(request(itemId, "48", pickup, pickup.plusDays(2))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(res, "$.id");
        assertThat(mailer.to(HONG)).hasSize(1);
        assertThat(mailer.to("ops@example.com")).hasSize(1);
        assertThat(mailer.to("ops@example.com").getFirst().subject()).contains("[대여 신청]").contains("블랙 턱시도 · 48");

        // 옷 정보를 고쳐도 신청 기록의 이름 · 금액은 그대로다
        mockMvc.perform(csrf(mockMvc, put("/api/admin/rental-items/" + itemId), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"네이비 턱시도\",\"priceKrw\":200000,\"rentalDays\":3,\"sizes\":[\"48\"]}")).andExpect(status().isOk());

        mockMvc.perform(csrf(mockMvc, post("/api/admin/rentals/" + id + "/confirm"), admin))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.itemName").value("블랙 턱시도"))
                .andExpect(jsonPath("$.priceKrw").value(150000))
                .andExpect(jsonPath("$.customer.name").value("홍길동"));
        assertThat(mailer.to(HONG)).hasSize(2);
        assertThat(mailer.to(HONG).getLast().subject()).contains("확정");

        mockMvc.perform(get("/api/admin/rentals").param("from", pickup.toString()).param("to", pickup.toString()).cookie(admin))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(csrf(mockMvc, put("/api/admin/rentals/" + id + "/note"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"adminNote\":\"바지 기장 수선\"}")).andExpect(jsonPath("$.adminNote").value("바지 기장 수선"));
        mockMvc.perform(get("/api/rentals/mine").cookie(hong))
                .andExpect(jsonPath("$[0].note").value("결혼식 하객"))
                .andExpect(jsonPath("$[0].adminNote").doesNotExist());

        // 반납 전에 반납 처리는 안 된다
        mockMvc.perform(csrf(mockMvc, post("/api/admin/rentals/" + id + "/return"), admin)).andExpect(status().isConflict());
        mockMvc.perform(csrf(mockMvc, post("/api/admin/rentals/" + id + "/pick-up"), admin))
                .andExpect(jsonPath("$.status").value("PICKED_UP")).andExpect(jsonPath("$.statusLabel").value("대여 중"));
        mockMvc.perform(csrf(mockMvc, post("/api/rentals/" + id + "/cancel"), hong)).andExpect(status().isConflict());
        mockMvc.perform(csrf(mockMvc, post("/api/me/withdraw"), hong).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RENTALS_IN_PROGRESS"));

        mockMvc.perform(csrf(mockMvc, post("/api/admin/rentals/" + id + "/return"), admin))
                .andExpect(jsonPath("$.status").value("RETURNED"));
        mockMvc.perform(csrf(mockMvc, post("/api/me/withdraw"), hong).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("취소 · 날짜 옮기기 — 손님은 수령일 전까지 취소(관리자 메일), 남의 대여는 404. 관리자가 옮기면 확정되고, 취소 사유는 손님에게 보인다")
    void cancelAndReschedule() throws Exception {
        String itemId = createItem("PUBLISHED");
        String a = JsonPath.read(mockMvc.perform(csrf(mockMvc, post("/api/rentals"), hong).contentType(MediaType.APPLICATION_JSON)
                .content(request(itemId, "46", pickup, pickup))).andReturn().getResponse().getContentAsString(), "$.id");
        String b = JsonPath.read(mockMvc.perform(csrf(mockMvc, post("/api/rentals"), hong).contentType(MediaType.APPLICATION_JSON)
                .content(request(itemId, "50", pickup, pickup))).andReturn().getResponse().getContentAsString(), "$.id");
        mailer.clear();

        Cookie kim = TestSupport.member(mockMvc, auth, "kim@example.com", "김철수");
        mockMvc.perform(csrf(mockMvc, post("/api/rentals/" + a + "/cancel"), kim)).andExpect(status().isNotFound());
        mockMvc.perform(csrf(mockMvc, post("/api/rentals/" + a + "/cancel"), hong))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(mailer.to("ops@example.com").getFirst().subject()).contains("[대여 취소]");

        LocalDate later = openDayFrom(pickup.plusDays(7));
        mockMvc.perform(csrf(mockMvc, put("/api/admin/rentals/" + b + "/schedule"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pickupDate\":\"%s\",\"returnDate\":\"%s\"}".formatted(later, later.plusDays(9))))
                .andExpect(status().isBadRequest()); // 옷의 기간(3일) 넘음
        mockMvc.perform(csrf(mockMvc, put("/api/admin/rentals/" + b + "/schedule"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pickupDate\":\"%s\",\"returnDate\":\"%s\"}".formatted(later, later)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.pickupDate").value(later.toString()));
        assertThat(mailer.to(HONG).getLast().subject()).contains("날짜가 바뀌었습니다");

        mockMvc.perform(csrf(mockMvc, post("/api/admin/rentals/" + b + "/cancel"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"수선 중\"}")).andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/rentals/mine").cookie(hong))
                .andExpect(jsonPath("$[0].cancelReason").value("수선 중"));
    }
}
