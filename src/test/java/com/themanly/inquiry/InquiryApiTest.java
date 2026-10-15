package com.themanly.inquiry;

import static com.themanly.TestSupport.PASSWORD;
import static com.themanly.TestSupport.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** 문의하기 — 비회원 · 회원 접수, 메일, 허니팟, 관리자 답변 · 종료, 권한. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
@TestPropertySource(properties = "app.notify.admin-emails=ops@example.com")
class InquiryApiTest {

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
    private TestMailConfiguration.RecordingMailer mailer;

    private Cookie hong;
    private Cookie admin;

    @BeforeEach
    void setUp() throws Exception {
        TestSupport.cleanDatabase(jdbc);
        mailer.clear();
        hong = TestSupport.member(mockMvc, auth, HONG, "홍길동");
        admins.createWithLoginId("opsadmin", "ops@example.com", "운영자", MemberRole.ADMIN, PASSWORD);
        admin = TestSupport.adminLogin(mockMvc, "opsadmin");
        jdbc.update("UPDATE member SET must_change_password = FALSE WHERE email = 'ops@example.com'");
    }

    @Test
    @DisplayName("비회원 문의 — 이름 · 이메일 필수, 접수되면 손님에게 확인 메일 · 관리자에게 새 문의 메일. 허니팟이 채워지면 저장하지 않는다")
    void guest() throws Exception {
        mockMvc.perform(csrf(mockMvc, post("/api/inquiries")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"GENERAL\",\"message\":\"결혼식 수트 문의드립니다.\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONTACT_REQUIRED"));
        mockMvc.perform(csrf(mockMvc, post("/api/inquiries")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"비회원\",\"email\":\"guest@example.com\",\"topic\":\"GENERAL\",\"message\":\"짧아요\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(csrf(mockMvc, post("/api/inquiries")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"비회원\",\"email\":\"guest@example.com\",\"topic\":\"GENERAL\",\"message\":\"결혼식 수트 문의드립니다.\",\"website\":\"http://spam\"}"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inquiry", Long.class)).isZero();

        mockMvc.perform(csrf(mockMvc, post("/api/inquiries")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"비회원\",\"email\":\"Guest@Example.com\",\"phone\":\"010-2222-3333\",\"topic\":\"FABRIC\",\"message\":\"네이비 원단 재고가 있나요? 11월에 필요합니다.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.topicLabel").value("원단 · 스타일"));
        assertThat(mailer.to("guest@example.com")).singleElement().satisfies(m -> assertThat(m.subject()).contains("문의를 받았습니다"));
        assertThat(mailer.to("ops@example.com")).singleElement().satisfies(m -> {
            assertThat(m.subject()).isEqualTo("[문의] 원단 · 스타일 · 비회원");
            assertThat(m.text()).contains("010-2222-3333").contains("네이비 원단");
        });
        mockMvc.perform(get("/api/inquiries/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("회원 문의 — 이름 · 이메일은 가입 정보로, 내 문의에서 보인다. 관리자가 답하면 메일이 가고 ANSWERED, 닫으면 더 답하지 못한다")
    void memberAndAnswer() throws Exception {
        String res = mockMvc.perform(csrf(mockMvc, post("/api/inquiries"), hong).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"남의이름\",\"email\":\"other@example.com\",\"topic\":\"ORDER\",\"message\":\"가봉 날짜를 바꾸고 싶습니다.\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(res, "$.id");
        assertThat(mailer.to(HONG)).hasSize(1);
        assertThat(mailer.to("other@example.com")).isEmpty();
        mockMvc.perform(get("/api/inquiries/mine").cookie(hong))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].answer").isEmpty());

        mockMvc.perform(get("/api/admin/inquiries").cookie(hong)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/inquiries").cookie(admin))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.openCount").value(1))
                .andExpect(jsonPath("$.items[0].name").value("홍길동"))
                .andExpect(jsonPath("$.items[0].email").value(HONG));
        mockMvc.perform(get("/api/admin/dashboard").cookie(admin)).andExpect(jsonPath("$.openInquiries").value(1));

        mockMvc.perform(csrf(mockMvc, post("/api/admin/inquiries/" + id + "/answer"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(csrf(mockMvc, post("/api/admin/inquiries/" + id + "/answer"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"토요일 2시로 옮겨 드렸습니다.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ANSWERED"));
        assertThat(mailer.to(HONG)).hasSize(2).last().satisfies(m -> {
            assertThat(m.subject()).contains("답해 드립니다");
            assertThat(m.text()).contains("토요일 2시로");
        });
        mockMvc.perform(get("/api/inquiries/mine").cookie(hong)).andExpect(jsonPath("$[0].answer").value("토요일 2시로 옮겨 드렸습니다."));
        mockMvc.perform(get("/api/admin/inquiries").cookie(admin).param("status", "OPEN")).andExpect(jsonPath("$.total").value(0));

        mockMvc.perform(csrf(mockMvc, post("/api/admin/inquiries/" + id + "/close"), admin)).andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(csrf(mockMvc, post("/api/admin/inquiries/" + id + "/answer"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"또 답\"}"))
                .andExpect(status().isConflict());
    }
}
