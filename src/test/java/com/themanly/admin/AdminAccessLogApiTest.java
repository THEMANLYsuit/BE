package com.themanly.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 관리자 접속 · 열람 기록 (V10) — 관리자 요청이 남고, 조회는 최고 관리자만. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
class AdminAccessLogApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuthService auth;
    @Autowired
    private AdminMemberService admins;

    private Cookie admin;
    private Cookie owner;
    private Cookie member;

    @BeforeEach
    void setUp() throws Exception {
        TestSupport.cleanDatabase(jdbc);
        admins.createWithLoginId("opsadmin", "ops@example.com", "운영자", MemberRole.ADMIN, TestSupport.PASSWORD);
        admins.createWithLoginId("owner", "owner@example.com", "대표", MemberRole.SUPER_ADMIN, TestSupport.PASSWORD);
        jdbc.update("UPDATE member SET must_change_password = FALSE WHERE email IN ('ops@example.com', 'owner@example.com')");
        admin = TestSupport.adminLogin(mockMvc, "opsadmin");
        owner = TestSupport.adminLogin(mockMvc, "owner");
        member = TestSupport.member(mockMvc, auth, "hong@example.com", "홍길동");
    }

    @Test
    @DisplayName("관리자가 관리자 API 를 부르면 누가 · 무엇을 · 어디서 · 결과가 남는다. 보안이 거절한 요청(회원 403 · 비로그인 401)은 남지 않는다")
    void recordsAdminRequests() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").cookie(admin).header("X-Forwarded-For", "203.0.113.9, 10.0.0.1"))
                .andExpect(status().isOk());
        var last = jdbc.queryForMap("SELECT actor_email, method, path, status, ip FROM admin_access_log ORDER BY id DESC LIMIT 1");
        assertThat(last.get("actor_email")).isEqualTo("ops@example.com");
        assertThat(last.get("method")).isEqualTo("GET");
        assertThat(last.get("path")).isEqualTo("/api/admin/dashboard");
        assertThat(((Number) last.get("status")).intValue()).isEqualTo(200);
        assertThat(last.get("ip")).isEqualTo("203.0.113.9");

        long before = jdbc.queryForObject("SELECT count(*) FROM admin_access_log", Long.class);
        mockMvc.perform(get("/api/admin/dashboard").cookie(member)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_access_log", Long.class)).isEqualTo(before);
        // 관리자가 통과한 요청은 결과가 404 여도 남는다
        mockMvc.perform(get("/api/admin/members/" + java.util.UUID.randomUUID() + "/overview").cookie(admin)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT status FROM admin_access_log ORDER BY id DESC LIMIT 1", Integer.class)).isEqualTo(404);
    }

    @Test
    @DisplayName("조회는 최고 관리자만 — 일반 관리자는 403. 경로 글자로 찾는다")
    void onlySuperAdminReads() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").cookie(admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/access-logs").cookie(admin)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/access-logs").cookie(owner).param("q", "dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actorEmail").value("ops@example.com"))
                .andExpect(jsonPath("$.items[0].path").value("/api/admin/dashboard"))
                .andExpect(jsonPath("$.total").value(1));
    }
}
