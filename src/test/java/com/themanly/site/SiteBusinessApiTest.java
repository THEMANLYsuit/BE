package com.themanly.site;

import static com.themanly.TestSupport.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.themanly.TestMailConfiguration;
import com.themanly.TestSupport;
import com.themanly.TestcontainersConfiguration;
import com.themanly.auth.AuthService;
import com.themanly.member.AdminMemberService;
import com.themanly.member.MemberRole;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

/** 푸터 사업자 정보 — 심은 값이 로그인 없이 읽히고, 관리자만 고치며, 형식이 틀리면 400. 고치면 손님 화면을 다시 만든다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
@RecordApplicationEvents
class SiteBusinessApiTest {

    private static final String SEEDED = """
            {"companyName":"레오네페리토 (LEONEFERITO)","representative":"조강현","registrationNumber":"881-15-02245",
             "mailOrderNumber":"2024-수원영통-0059","address":"경기도 수원시 영통구 매영로 425번길 1 1층 더맨리",
             "phone":"010-2434-7794","email":"sport0308@naver.com","privacyOfficer":"조강현 (대표)",
             "hosting":"Amazon Web Services (서울)"}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuthService auth;
    @Autowired
    private AdminMemberService admins;
    @Autowired
    private ApplicationEvents events;

    private Cookie admin;
    private Cookie member;

    @BeforeEach
    void setUp() throws Exception {
        TestSupport.cleanDatabase(jdbc);
        admins.createWithLoginId("opsadmin", "ops@example.com", "운영자", MemberRole.ADMIN, TestSupport.PASSWORD);
        jdbc.update("UPDATE member SET must_change_password = FALSE WHERE email = 'ops@example.com'");
        admin = TestSupport.adminLogin(mockMvc, "opsadmin");
        member = TestSupport.member(mockMvc, auth, "hong@example.com", "홍길동");
    }

    @AfterEach
    void restore() throws Exception {
        // 다른 테스트가 심은 값을 기대하므로 되돌린다
        mockMvc.perform(csrf(mockMvc, put("/api/admin/site-business"), admin).contentType(MediaType.APPLICATION_JSON).content(SEEDED));
    }

    @Test
    @DisplayName("V13 이 심은 값이 로그인 없이 읽힌다")
    void seededAndPublic() throws Exception {
        mockMvc.perform(get("/api/site-business"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("레오네페리토 (LEONEFERITO)"))
                .andExpect(jsonPath("$.registrationNumber").value("881-15-02245"))
                .andExpect(jsonPath("$.phone").value("010-2434-7794"));
    }

    @Test
    @DisplayName("관리자가 고치면 바로 읽히고 손님 화면을 다시 만든다. 회원은 못 고친다. 사업자등록번호 · 이메일 · 빈 칸은 400")
    void adminSaves() throws Exception {
        String changed = SEEDED.replace("010-2434-7794", "031-000-0000");
        mockMvc.perform(csrf(mockMvc, put("/api/admin/site-business"), member).contentType(MediaType.APPLICATION_JSON).content(changed))
                .andExpect(status().isForbidden());
        mockMvc.perform(csrf(mockMvc, put("/api/admin/site-business"), admin).contentType(MediaType.APPLICATION_JSON).content(changed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("031-000-0000"));
        mockMvc.perform(get("/api/site-business")).andExpect(jsonPath("$.phone").value("031-000-0000"));
        assertThat(events.stream(FrontRebuildTrigger.SiteChanged.class).count()).isEqualTo(1);

        for (String bad : new String[] {
                SEEDED.replace("881-15-02245", "8811502245"),
                SEEDED.replace("sport0308@naver.com", "not-an-email"),
                SEEDED.replace("\"조강현\"", "\"\"")}) {
            mockMvc.perform(csrf(mockMvc, put("/api/admin/site-business"), admin).contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isBadRequest());
        }
    }
}
