package com.themanly;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.themanly.auth.AuthService;
import com.themanly.member.AdminMemberService;
import com.themanly.member.MemberRole;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 테스트 공용 — 진짜 로그인(세션 쿠키 TMSESSION)과 CSRF. 가짜 인증({@code with(user(...))})을 쓰지 않는다:
 * 세션이 실제로 DB 에 있어야 "세션이 끊겼는가" 를 볼 수 있다.
 */
public final class TestSupport {

    public static final String PASSWORD = "long enough secret 9";

    private TestSupport() {
    }

    /** 테스트 사이 정리. FK 순서대로. */
    public static void cleanDatabase(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM admin_access_log");
        jdbc.update("DELETE FROM film");
        jdbc.update("DELETE FROM media_asset");
        jdbc.update("DELETE FROM inquiry");
        jdbc.update("DELETE FROM appointment");
        jdbc.update("DELETE FROM bespoke_order");
        jdbc.update("DELETE FROM measurement");
        jdbc.update("DELETE FROM password_reset_token");
        jdbc.update("DELETE FROM member");
        jdbc.update("DELETE FROM fabric");
        jdbc.update("DELETE FROM style_option");
    }

    /** 손님 하나 가입 + 로그인. */
    public static Cookie member(MockMvc mockMvc, AuthService auth, String email, String name) throws Exception {
        auth.signup(email, PASSWORD, name, null);
        return login(mockMvc, email);
    }

    /** 관리자 하나(서버 명령과 같은 길) + 로그인. */
    public static Cookie admin(MockMvc mockMvc, AdminMemberService admins, String email, MemberRole role) throws Exception {
        admins.createWithLoginId(email.substring(0, email.indexOf('@')).replaceAll("[^a-z0-9_]", ""), email, "운영자", role, PASSWORD);
        // 임시 비밀번호 표시가 있으면 관리자 API 가 막힌다 — 테스트에서는 바로 바꾼 것으로 본다
        return adminLogin(mockMvc, email);
    }

    public static Cookie login(MockMvc mockMvc, String email) throws Exception {
        return sessionOf(mockMvc.perform(csrf(mockMvc, post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))).andExpect(status().isOk()).andReturn());
    }

    public static Cookie adminLogin(MockMvc mockMvc, String loginIdOrEmail) throws Exception {
        return sessionOf(mockMvc.perform(csrf(mockMvc, post("/api/auth/admin-login")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(loginIdOrEmail, PASSWORD))).andExpect(status().isOk()).andReturn());
    }

    private static Cookie sessionOf(MvcResult result) {
        Cookie session = result.getResponse().getCookie("TMSESSION");
        if (session == null) {
            throw new AssertionError("no session cookie: " + result.getResponse().getHeaders("Set-Cookie"));
        }
        return session;
    }

    /** CSRF 토큰을 받아 요청에 붙인다. 세션 쿠키가 있으면 같이 싣는다. */
    public static MockHttpServletRequestBuilder csrf(MockMvc mockMvc, MockHttpServletRequestBuilder builder, Cookie... cookies)
            throws Exception {
        MvcResult tokenResult = mockMvc.perform(get("/api/auth/csrf")).andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.token");
        Cookie csrfCookie = tokenResult.getResponse().getCookie("XSRF-TOKEN");
        List<Cookie> all = new ArrayList<>(List.of(cookies));
        all.add(csrfCookie == null ? new Cookie("XSRF-TOKEN", token) : csrfCookie);
        return builder.header("X-XSRF-TOKEN", token).cookie(all.toArray(Cookie[]::new));
    }

    public static long sessionCount(JdbcTemplate jdbc, String email) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?", Long.class, email);
        return count == null ? 0 : count;
    }
}
