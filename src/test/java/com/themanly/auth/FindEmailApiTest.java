package com.themanly.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.themanly.TestMailConfiguration;
import com.themanly.TestSupport;
import com.themanly.TestcontainersConfiguration;
import com.themanly.common.error.DomainRuleException;
import com.themanly.member.MemberRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * 아이디 찾기 — 이름 · 휴대폰 번호로 가린 이메일. 지키려는 것: 가린 모양, 관리자 · 정지 회원은 안 나옴,
 * 하이픈 · 대소문자 · 공백 무시, 없으면 빈 목록, 로그인 없이 된다, 같은 IP 는 횟수 제한.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
class FindEmailApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuthService auth;
    @Autowired
    private MemberRepository members;

    @BeforeEach
    void setUp() {
        TestSupport.cleanDatabase(jdbc);
    }

    /** 테스트마다 다른 IP — 횟수 제한이 테스트끼리 섞이지 않게 */
    private ResultActions find(String name, String phone, String ip) throws Exception {
        return mockMvc.perform(TestSupport.csrf(mockMvc, post("/api/auth/find-email"))
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"phone\":\"%s\"}".formatted(name, phone)));
    }

    @Test
    @DisplayName("이름 · 번호가 같으면 가린 이메일 · 가입일 — 하이픈 · 대소문자 · 공백은 상관없다")
    void findsMasked() throws Exception {
        auth.signup("hong.gildong@naver.com", "correct horse battery", "Hong Gildong", "010-1234-5678");

        find("  hong gildong ", "01012345678", "10.0.0.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts.length()").value(1))
                .andExpect(jsonPath("$.accounts[0].maskedEmail").value("ho**********@naver.com"))
                .andExpect(jsonPath("$.accounts[0].joinedOn").value(LocalDate.now(ZoneId.of("Asia/Seoul")).toString()));
        find("Hong Gildong", "010-1234-5678", "10.0.0.1").andExpect(jsonPath("$.accounts.length()").value(1));
    }

    @Test
    @DisplayName("없으면 빈 목록(200) · 관리자 · 정지 회원은 안 나온다")
    void noMatchAndHidden() throws Exception {
        auth.signup("kim@example.com", "correct horse battery", "김철수", "010-2222-3333");
        auth.signup("boss@example.com", "correct horse battery", "이대표", "010-9999-0000");
        auth.signup("bad@example.com", "correct horse battery", "이대표", "010-9999-0000");
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE email = 'boss@example.com'");
        jdbc.update("UPDATE member SET status = 'SUSPENDED' WHERE email = 'bad@example.com'");

        find("김철수", "010-2222-3334", "10.0.0.2").andExpect(status().isOk()).andExpect(jsonPath("$.accounts.length()").value(0));
        find("김철수", "010-2222-3333", "10.0.0.2").andExpect(jsonPath("$.accounts[0].maskedEmail").value("ki*@example.com"));
        find("이대표", "01099990000", "10.0.0.2").andExpect(jsonPath("$.accounts.length()").value(0));
    }

    @Test
    @DisplayName("번호 형식이 틀리면 400 — 로그인 없이 부를 수 있다(401 이 아니다)")
    void validation() throws Exception {
        find("홍길동", "phone", "10.0.0.3").andExpect(status().isBadRequest());
        find("", "010-1234-5678", "10.0.0.3").andExpect(status().isBadRequest());
        assertThat(members.count()).isZero();
    }

    @Test
    @DisplayName("같은 IP 는 10번까지, 11번째는 429 — 다른 IP 는 상관없다")
    void rateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            find("홍길동", "0101234000" + i, "10.0.0.4").andExpect(status().isOk());
        }
        find("홍길동", "01012340000", "10.0.0.4")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        find("홍길동", "01012340000", "10.0.0.5").andExpect(status().isOk());
    }

    @Test
    @DisplayName("창이 지나면 다시 받는다 · 가리는 규칙")
    void windowAndMask() {
        FindEmailService s = new FindEmailService(members);
        Instant t0 = Instant.parse("2026-10-19T00:00:00Z");
        for (int i = 0; i < 10; i++) {
            s.allow("1.2.3.4", t0);
        }
        assertThatThrownBy(() -> s.allow("1.2.3.4", t0.plusSeconds(60))).isInstanceOf(DomainRuleException.class);
        s.allow("1.2.3.4", t0.plusSeconds(601));

        assertThat(FindEmailService.mask("hong@gmail.com")).isEqualTo("ho**@gmail.com");
        assertThat(FindEmailService.mask("ab@x.kr")).isEqualTo("a*@x.kr");
    }
}
