package com.themanly.auth;

import static com.themanly.TestSupport.PASSWORD;
import static com.themanly.TestSupport.csrf;
import static com.themanly.TestSupport.login;
import static com.themanly.TestSupport.sessionCount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.themanly.TestMailConfiguration;
import com.themanly.TestSupport;
import com.themanly.TestcontainersConfiguration;
import com.themanly.member.AdminMemberService;
import com.themanly.member.Member;
import com.themanly.member.MemberRepository;
import com.themanly.member.MemberRole;
import jakarta.servlet.http.Cookie;
import java.net.URI;
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

/** 가입 · 로그인 · 잠금 · 관리자 로그인 · 비밀번호 설정 링크 · 내 정보. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
class AuthApiTest {

    private static final String EMAIL = "hong@example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuthService auth;
    @Autowired
    private AdminMemberService admins;
    @Autowired
    private MemberRepository members;
    @Autowired
    private PasswordResetService passwordReset;
    @Autowired
    private TestMailConfiguration.RecordingMailer mailer;

    @BeforeEach
    void setUp() {
        TestSupport.cleanDatabase(jdbc);
        mailer.clear();
    }

    private String signupBody(String email, String password) {
        return """
                {"email":"%s","password":"%s","name":"홍길동","phone":"010-1234-5678","agreeTerms":true,"over14":true}
                """.formatted(email, password);
    }

    @Test
    @DisplayName("가입 → 로그인 → 내 정보. 가입 직후 자동 로그인은 없다. 이메일은 소문자로 저장된다")
    void signupAndLogin() throws Exception {
        mockMvc.perform(csrf(mockMvc, post("/api/auth/signup")).contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("Hong@Example.com", PASSWORD)))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        Cookie session = login(mockMvc, EMAIL);
        mockMvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_MEMBER"));
        assertThat(members.findByEmail(EMAIL).orElseThrow().getTermsVersion()).isEqualTo(Member.CURRENT_TERMS_VERSION);

        // 약관 동의 없이는 400 · 같은 이메일은 409 · 짧은 비밀번호는 400
        mockMvc.perform(csrf(mockMvc, post("/api/auth/signup")).contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("kim@example.com", PASSWORD).replace("\"agreeTerms\":true", "\"agreeTerms\":false")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(csrf(mockMvc, post("/api/auth/signup")).contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody(EMAIL, PASSWORD)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        mockMvc.perform(csrf(mockMvc, post("/api/auth/signup")).contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("kim@example.com", "short")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    @DisplayName("비밀번호를 5번 틀리면 잠긴다 — 잠겼다는 말은 맞는 비밀번호에만 한다. 로그아웃하면 세션이 DB 에서 사라진다")
    void lockAndLogout() throws Exception {
        auth.signup(EMAIL, PASSWORD, "홍길동", null);
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(csrf(mockMvc, post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"%s\",\"password\":\"wrong password!\"}".formatted(EMAIL)))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        mockMvc.perform(csrf(mockMvc, post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, PASSWORD)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        // 없는 계정도 같은 답
        mockMvc.perform(csrf(mockMvc, post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        jdbc.update("UPDATE member SET locked_until = NULL, failed_login_attempts = 0 WHERE email = ?", EMAIL);
        Cookie session = login(mockMvc, EMAIL);
        assertThat(sessionCount(jdbc, EMAIL)).isEqualTo(1);
        mockMvc.perform(csrf(mockMvc, post("/api/auth/logout"), session)).andExpect(status().isNoContent());
        assertThat(sessionCount(jdbc, EMAIL)).isZero();
        mockMvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("관리자 — 서버 명령과 같은 길로 만든 아이디로 /admin-login. 손님은 관리자 로그인도 관리자 API 도 안 된다")
    void adminLogin() throws Exception {
        admins.createWithLoginId("masteradmin", "ops@example.com", "운영자", MemberRole.SUPER_ADMIN, PASSWORD);
        Cookie admin = TestSupport.adminLogin(mockMvc, "masteradmin");
        mockMvc.perform(get("/api/auth/me").cookie(admin))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        // 임시 비밀번호인 동안 관리자 API 는 403
        mockMvc.perform(get("/api/admin/members").cookie(admin)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(csrf(mockMvc, post("/api/me/password"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"brand new secret 42\"}".formatted(PASSWORD)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/members").cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].loginId").value("masteradmin"));

        auth.signup(EMAIL, PASSWORD, "홍길동", null);
        mockMvc.perform(csrf(mockMvc, post("/api/auth/admin-login")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, PASSWORD)))
                .andExpect(status().isUnauthorized());
        Cookie member = login(mockMvc, EMAIL);
        mockMvc.perform(get("/api/admin/members").cookie(member)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("비밀번호 설정 링크 — 명령이 찍은 링크로 새 비밀번호를 정하면 옛 세션이 끊기고, 링크는 한 번만 쓴다")
    void passwordResetLink() throws Exception {
        auth.signup(EMAIL, PASSWORD, "홍길동", null);
        Cookie old = login(mockMvc, EMAIL);
        String link = passwordReset.issueLink(members.findByEmail(EMAIL).orElseThrow());
        String token = URI.create(link).getQuery().substring("token=".length());
        assertThat(link).startsWith("http://localhost:3001/reset/?token=");

        mockMvc.perform(csrf(mockMvc, post("/api/auth/password-reset/confirm")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"newPassword\":\"another long secret 7\"}".formatted(token)))
                .andExpect(status().isNoContent());
        assertThat(sessionCount(jdbc, EMAIL)).isZero();
        mockMvc.perform(get("/api/auth/me").cookie(old)).andExpect(status().isUnauthorized());
        mockMvc.perform(csrf(mockMvc, post("/api/auth/password-reset/confirm")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"newPassword\":\"yet another secret 8\"}".formatted(token)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));

        // 비밀번호 찾기 — 가입 여부와 관계없이 202. 가입된 주소에는 메일이 간다
        mockMvc.perform(csrf(mockMvc, post("/api/auth/password-reset/request")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}")).andExpect(status().isAccepted());
        mockMvc.perform(csrf(mockMvc, post("/api/auth/password-reset/request")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(EMAIL))).andExpect(status().isAccepted());
        assertThat(mailer.to(EMAIL)).singleElement().satisfies(m -> assertThat(m.text()).contains("/reset/?token="));
    }

    @Test
    @DisplayName("내 정보 수정 — 이름을 바꾸면 세션의 이름도 바뀐다. 탈퇴하면 세션이 끝나고 같은 이메일로 다시 가입할 수 있다")
    void profileAndWithdraw() throws Exception {
        auth.signup(EMAIL, PASSWORD, "홍길동", null);
        Cookie session = login(mockMvc, EMAIL);
        mockMvc.perform(csrf(mockMvc, patch("/api/me/profile"), session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"홍길순\",\"phone\":\"010-9999-0000\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("010-9999-0000"));
        mockMvc.perform(get("/api/auth/me").cookie(session)).andExpect(jsonPath("$.name").value("홍길순"));

        mockMvc.perform(csrf(mockMvc, post("/api/me/withdraw"), session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong password!\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
        mockMvc.perform(csrf(mockMvc, post("/api/me/withdraw"), session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isNoContent());
        assertThat(sessionCount(jdbc, EMAIL)).isZero();
        assertThat(members.findByEmail(EMAIL)).isEmpty();
        mockMvc.perform(csrf(mockMvc, post("/api/auth/signup")).contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody(EMAIL, PASSWORD))).andExpect(status().isCreated());
    }
}
