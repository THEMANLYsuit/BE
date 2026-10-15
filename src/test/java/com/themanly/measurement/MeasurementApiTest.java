package com.themanly.measurement;

import static com.themanly.TestSupport.PASSWORD;
import static com.themanly.TestSupport.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/** 치수 기록 — 관리자가 적고 고치고 지운다, 손님은 자기 것만 본다(운영자 메모 없이), 값 검사, 탈퇴하면 지운다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
class MeasurementApiTest {

    private static final String HONG = "hong@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuthService auth;
    @Autowired
    private AdminMemberService admins;

    private Cookie hong;
    private Cookie kim;
    private Cookie admin;
    private String hongId;

    @BeforeEach
    void setUp() throws Exception {
        TestSupport.cleanDatabase(jdbc);
        hong = TestSupport.member(mockMvc, auth, HONG, "홍길동");
        kim = TestSupport.member(mockMvc, auth, "kim@example.com", "김철수");
        admins.createWithLoginId("opsadmin", "ops@example.com", "운영자", MemberRole.ADMIN, PASSWORD);
        admin = TestSupport.adminLogin(mockMvc, "opsadmin");
        jdbc.update("UPDATE member SET must_change_password = FALSE WHERE email = 'ops@example.com'");
        hongId = jdbc.queryForObject("SELECT id::text FROM member WHERE email = ?", String.class, HONG);
    }

    private String body(String points) {
        return "{\"measuredOn\":\"%s\",\"points\":%s,\"bodyNote\":\"오른어깨 처짐\",\"adminNote\":\"허리 넉넉히 선호\"}"
                .formatted(LocalDate.now(), points);
    }

    @Test
    @DisplayName("관리자가 적으면 손님은 자기 치수를 본다 — 부위 이름 · 묶음 · 단위가 붙고, 운영자 메모는 없다. 남의 것은 안 보인다")
    void recordAndSee() throws Exception {
        mockMvc.perform(get("/api/admin/measurement-points").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(24));

        mockMvc.perform(csrf(mockMvc, post("/api/admin/members/" + hongId + "/measurements"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"CHEST\":98.5,\"HEIGHT\":178,\"WEIGHT\":72.4}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.adminNote").value("허리 넉넉히 선호"))
                .andExpect(jsonPath("$.points.length()").value(3));

        mockMvc.perform(get("/api/me/measurements").cookie(hong))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bodyNote").value("오른어깨 처짐"))
                .andExpect(jsonPath("$[0].adminNote").doesNotExist())
                // 화면 순서: 체형(키 · 몸무게) → 상의(가슴)
                .andExpect(jsonPath("$[0].points[0].point").value("HEIGHT"))
                .andExpect(jsonPath("$[0].points[1].unit").value("kg"))
                .andExpect(jsonPath("$[0].points[2].label").value("가슴둘레"))
                .andExpect(jsonPath("$[0].points[2].groupLabel").value("상의"))
                .andExpect(jsonPath("$[0].points[2].value").value(98.5));
        mockMvc.perform(get("/api/me/measurements").cookie(kim)).andExpect(jsonPath("$.length()").value(0));

        // 손님은 관리자 API 를 못 쓴다
        mockMvc.perform(csrf(mockMvc, post("/api/admin/members/" + hongId + "/measurements"), hong).contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"CHEST\":98.5}")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("값 검사 — 범위 밖 · 소수점 둘째 자리 · 빈 치수 · 모르는 부위는 400, 메시지에 부위 이름이 있다")
    void validation() throws Exception {
        String url = "/api/admin/members/" + hongId + "/measurements";
        mockMvc.perform(csrf(mockMvc, post(url), admin).contentType(MediaType.APPLICATION_JSON).content(body("{\"CHEST\":9.8}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEASUREMENT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("가슴둘레")));
        mockMvc.perform(csrf(mockMvc, post(url), admin).contentType(MediaType.APPLICATION_JSON).content(body("{\"NECK\":40.25}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MEASUREMENT"));
        mockMvc.perform(csrf(mockMvc, post(url), admin).contentType(MediaType.APPLICATION_JSON).content(body("{}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMPTY_MEASUREMENT"));
        mockMvc.perform(csrf(mockMvc, post(url), admin).contentType(MediaType.APPLICATION_JSON).content(body("{\"NOSE\":5}")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(csrf(mockMvc, post(url), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"measuredOn\":\"%s\",\"points\":{\"CHEST\":98}}".formatted(LocalDate.now().plusDays(3))))
                .andExpect(status().isBadRequest()); // 앞날짜
        assertThat(jdbc.queryForObject("SELECT count(*) FROM measurement", Long.class)).isZero();
    }

    @Test
    @DisplayName("고치면 통째로 바뀐다(빠진 부위는 지워짐), 지우면 사라진다. 기록은 최근 것부터")
    void updateAndDelete() throws Exception {
        String url = "/api/admin/members/" + hongId + "/measurements";
        mockMvc.perform(csrf(mockMvc, post(url), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"measuredOn\":\"%s\",\"points\":{\"CHEST\":96}}".formatted(LocalDate.now().minusYears(1))));
        String res = mockMvc.perform(csrf(mockMvc, post(url), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"CHEST\":98.5,\"WAIST\":84}")))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(res, "$.id");

        mockMvc.perform(get(url).cookie(admin)).andExpect(jsonPath("$[0].id").value(id)).andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(csrf(mockMvc, put("/api/admin/measurements/" + id), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"CHEST\":99}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(1))
                .andExpect(jsonPath("$.points[0].value").value(99.0));

        mockMvc.perform(csrf(mockMvc, delete("/api/admin/measurements/" + id), admin)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/measurements").cookie(hong)).andExpect(jsonPath("$.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM measurement_point", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴하면 치수 기록이 지워진다")
    void withdrawDeletes() throws Exception {
        mockMvc.perform(csrf(mockMvc, post("/api/admin/members/" + hongId + "/measurements"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body("{\"CHEST\":98.5}")))
                .andExpect(status().isCreated());
        mockMvc.perform(csrf(mockMvc, post("/api/me/withdraw"), hong).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM measurement", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM measurement_point", Long.class)).isZero();
    }
}
