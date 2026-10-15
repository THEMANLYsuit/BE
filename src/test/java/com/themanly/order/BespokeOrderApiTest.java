package com.themanly.order;

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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 맞춤 주문 — 관리자가 만들고 단계를 옮기고 안내 · 수령 완료 · 취소, 손님은 자기 주문의 진행을 본다, 메일, 탈퇴 제한. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
class BespokeOrderApiTest {

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
    private TestMailConfiguration.RecordingMailer mailer;

    private Cookie hong;
    private Cookie kim;
    private Cookie admin;
    private String hongId;
    private String kimId;
    private String fabricId;
    private String hongMeasurementId;
    private String kimMeasurementId;

    @BeforeEach
    void setUp() throws Exception {
        TestSupport.cleanDatabase(jdbc);
        mailer.clear();
        hong = TestSupport.member(mockMvc, auth, HONG, "홍길동");
        kim = TestSupport.member(mockMvc, auth, KIM, "김철수");
        admins.createWithLoginId("opsadmin", "ops@example.com", "운영자", MemberRole.ADMIN, PASSWORD);
        admin = TestSupport.adminLogin(mockMvc, "opsadmin");
        jdbc.update("UPDATE member SET must_change_password = FALSE WHERE email = 'ops@example.com'");
        hongId = jdbc.queryForObject("SELECT id::text FROM member WHERE email = ?", String.class, HONG);
        kimId = jdbc.queryForObject("SELECT id::text FROM member WHERE email = ?", String.class, KIM);

        fabricId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO fabric (id, code, name, mill, status) VALUES (?::uuid, 'VBC-1021', '네이비 트로피컬', 'VBC', 'PUBLISHED')", fabricId);
        jdbc.update("INSERT INTO style_option (id, category, code, name) VALUES (?::uuid, 'LAPEL', 'peak', '피크 라펠')", UUID.randomUUID().toString());
        hongMeasurementId = measure(hongId);
        kimMeasurementId = measure(kimId);
        mailer.clear();
    }

    private String measure(String memberId) throws Exception {
        String res = mockMvc.perform(csrf(mockMvc, post("/api/admin/members/" + memberId + "/measurements"), admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"measuredOn\":\"%s\",\"points\":{\"CHEST\":98}}".formatted(LocalDate.now())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.id");
    }

    private String order(String extra) {
        return ("{\"garment\":\"SUIT_2PC\",\"title\":\"결혼식 네이비 수트\",\"fabricId\":\"%s\",\"styles\":{\"LAPEL\":\"peak\"},"
                + "\"priceKrw\":1500000,\"depositKrw\":500000,\"adminNote\":\"왼어깨 처짐 보정\",\"message\":\"11월 결혼식에 맞춰 진행합니다.\"%s}")
                .formatted(fabricId, extra);
    }

    private String open(String body) throws Exception {
        String res = mockMvc.perform(csrf(mockMvc, post("/api/admin/members/" + hongId + "/orders"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.order.summary.orderNumber");
    }

    private org.springframework.test.web.servlet.ResultActions stage(String no, String stage, String message) throws Exception {
        return mockMvc.perform(csrf(mockMvc, post("/api/admin/orders/" + no + "/stage"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"%s\",\"message\":\"%s\"}".formatted(stage, message)));
    }

    @Test
    @DisplayName("주문 시작 → 단계 옮기기 → 수령 완료. 손님은 진행 막대 · 기록을 보고(운영자 메모 없이), 단계마다 메일을 받는다")
    void lifecycle() throws Exception {
        String no = open(order(""));
        assertThat(no).matches("^TM\\d{8}-[A-Z2-9]{8}$");
        assertThat(mailer.to(HONG)).singleElement().satisfies(m -> {
            assertThat(m.subject()).isEqualTo("[THE MANLY] 맞춤 주문을 시작합니다");
            assertThat(m.text()).contains("주문번호  " + no).contains("11월 결혼식에 맞춰 진행합니다.").contains("/mypage/order/?no=" + no);
        });

        mockMvc.perform(get("/api/orders/mine").cookie(hong))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].stageLabel").value("상담"))
                .andExpect(jsonPath("$[0].garmentLabel").value("투피스 수트"));
        mockMvc.perform(get("/api/orders/mine/" + no).cookie(hong))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fabric.name").value("네이비 트로피컬"))
                .andExpect(jsonPath("$.styles[0].name").value("피크 라펠"))
                .andExpect(jsonPath("$.measuredOn").value(LocalDate.now().toString())) // 최근 치수가 저절로 붙는다
                .andExpect(jsonPath("$.balanceKrw").value(1000000))
                .andExpect(jsonPath("$.steps[0].current").value(true))
                .andExpect(jsonPath("$.timeline.length()").value(1))
                .andExpect(jsonPath("$.adminNote").doesNotExist())
                .andExpect(jsonPath("$..adminNote").isEmpty());
        mockMvc.perform(get("/api/orders/mine/" + no).cookie(kim)).andExpect(status().isNotFound());

        stage(no, "FITTING", "가봉 날짜를 잡아 주세요").andExpect(status().isOk())
                .andExpect(jsonPath("$.order.summary.stage").value("FITTING"))
                .andExpect(jsonPath("$.order.steps[2].done").value(true))
                .andExpect(jsonPath("$.order.steps[3].current").value(true));
        assertThat(mailer.to(HONG)).anySatisfy(m -> assertThat(m.subject()).isEqualTo("[THE MANLY] 결혼식 네이비 수트 — 가봉 단계입니다"));
        stage(no, "FITTING", "").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SAME_STAGE"));

        mockMvc.perform(csrf(mockMvc, post("/api/admin/orders/" + no + "/complete"), admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_DELIVERY"));
        mockMvc.perform(csrf(mockMvc, post("/api/admin/orders/" + no + "/notes"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"가봉은 토요일 2시로 잡았습니다.\"}"))
                .andExpect(status().isOk());
        assertThat(mailer.to(HONG)).anySatisfy(m -> assertThat(m.text()).contains("가봉은 토요일 2시로 잡았습니다."));

        stage(no, "DELIVERY", "완성되었습니다. 찾으러 오세요.").andExpect(status().isOk());
        mockMvc.perform(csrf(mockMvc, post("/api/admin/orders/" + no + "/complete"), admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.summary.status").value("COMPLETED"))
                .andExpect(jsonPath("$.order.steps[5].done").value(true));

        mockMvc.perform(get("/api/orders/mine/" + no).cookie(hong))
                .andExpect(jsonPath("$.summary.statusLabel").value("수령 완료"))
                .andExpect(jsonPath("$.timeline.length()").value(5))
                .andExpect(jsonPath("$.timeline[0].kind").value("COMPLETED")); // 최근 것부터
        assertThat(mailer.to(HONG)).hasSize(5);
        stage(no, "FITTING", "").andExpect(status().isConflict()); // 끝난 주문은 옮기지 않는다
    }

    @Test
    @DisplayName("넣기 전 검사 — 계약금 > 총액, 없는 스타일, 남의 치수, 없는 원단은 400. 손님은 주문을 만들 수 없다")
    void validation() throws Exception {
        String url = "/api/admin/members/" + hongId + "/orders";
        mockMvc.perform(csrf(mockMvc, post(url), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(order("").replace("\"depositKrw\":500000", "\"depositKrw\":2000000")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DEPOSIT_OVER_PRICE"));
        mockMvc.perform(csrf(mockMvc, post(url), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(order("").replace("\"peak\"", "\"shawl\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_STYLE"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("라펠")));
        mockMvc.perform(csrf(mockMvc, post(url), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(order(",\"measurementId\":\"%s\"".formatted(kimMeasurementId))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_MEASUREMENT"));
        mockMvc.perform(csrf(mockMvc, post(url), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(order("").replace(fabricId, UUID.randomUUID().toString())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_FABRIC"));
        mockMvc.perform(csrf(mockMvc, post(url), hong).contentType(MediaType.APPLICATION_JSON).content(order("")))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bespoke_order", Long.class)).isZero();
        assertThat(mailer.sent()).isEmpty();

        // 내 치수를 고르면 그대로 붙는다
        String no = open(order(",\"measurementId\":\"%s\"".formatted(hongMeasurementId)));
        mockMvc.perform(get("/api/admin/orders/" + no).cookie(admin))
                .andExpect(jsonPath("$.measurementId").value(hongMeasurementId))
                .andExpect(jsonPath("$.adminNote").value("왼어깨 처짐 보정"))
                .andExpect(jsonPath("$.customer.name").value("홍길동"))
                .andExpect(jsonPath("$.styleCodes.LAPEL").value("peak"));
    }

    @Test
    @DisplayName("관리자 고치기 · 검색 · 취소. 취소된 주문은 고치지 못하고, 손님에게 사유가 간다")
    void editSearchCancel() throws Exception {
        String no = open(order(""));
        mockMvc.perform(csrf(mockMvc, put("/api/admin/orders/" + no), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(order(",\"dueOn\":\"2026-11-20\"").replace("\"styles\":{\"LAPEL\":\"peak\"}", "\"styles\":{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.summary.dueOn").value("2026-11-20"))
                .andExpect(jsonPath("$.order.styles.length()").value(0))
                .andExpect(jsonPath("$.order.timeline.length()").value(1)); // 고치기는 기록을 남기지 않는다

        mockMvc.perform(get("/api/admin/orders").cookie(admin).param("q", "홍길"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].customer.email").value(HONG));
        mockMvc.perform(get("/api/admin/orders").cookie(admin).param("q", no.toLowerCase())).andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(get("/api/admin/orders").cookie(admin).param("status", "COMPLETED")).andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/api/admin/members/" + hongId + "/orders").cookie(admin)).andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(csrf(mockMvc, post("/api/admin/orders/" + no + "/cancel"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"손님 요청\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.summary.status").value("CANCELLED"))
                .andExpect(jsonPath("$.order.cancelReason").value("손님 요청"));
        assertThat(mailer.to(HONG)).anySatisfy(m -> {
            assertThat(m.subject()).endsWith("주문이 취소되었습니다");
            assertThat(m.text()).contains("사유  손님 요청");
        });
        mockMvc.perform(csrf(mockMvc, put("/api/admin/orders/" + no), admin).contentType(MediaType.APPLICATION_JSON).content(order("")))
                .andExpect(status().isConflict());
        mockMvc.perform(csrf(mockMvc, post("/api/admin/orders/" + no + "/notes"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"안내\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("진행 중인 맞춤 주문이 있으면 탈퇴할 수 없다. 끝나면 탈퇴되고 치수는 지워지며 주문의 치수 연결만 끊긴다")
    void withdraw() throws Exception {
        String no = open(order(""));
        String withdraw = "{\"password\":\"%s\"}".formatted(PASSWORD);
        mockMvc.perform(csrf(mockMvc, post("/api/me/withdraw"), hong).contentType(MediaType.APPLICATION_JSON).content(withdraw))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDERS_IN_PROGRESS"));

        stage(no, "DELIVERY", "").andExpect(status().isOk());
        mockMvc.perform(csrf(mockMvc, post("/api/admin/orders/" + no + "/complete"), admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(csrf(mockMvc, post("/api/me/withdraw"), hong).contentType(MediaType.APPLICATION_JSON).content(withdraw))
                .andExpect(status().isNoContent());
        List<String> left = jdbc.queryForList("SELECT coalesce(measurement_id::text, 'none') FROM bespoke_order", String.class);
        assertThat(left).containsExactly("none");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM measurement WHERE member_id = ?::uuid", Long.class, hongId)).isZero();
    }
}
