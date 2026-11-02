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

/** 사이트 문구(V14 · 새 메인 V17) —심은 글이 로그인 없이 읽히고, 관리자만 통째로 고치며, 칸 수 · 빈 칸 · 길이가 틀리면 400. 고치면 다시 빌드. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
@RecordApplicationEvents
class SiteCopyApiTest {

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
    /** V14 가 심은 글 그대로 — 테스트가 끝나면 되돌린다 */
    private String seeded;

    @BeforeEach
    void setUp() throws Exception {
        TestSupport.cleanDatabase(jdbc);
        admins.createWithLoginId("opsadmin", "ops@example.com", "운영자", MemberRole.ADMIN, TestSupport.PASSWORD);
        jdbc.update("UPDATE member SET must_change_password = FALSE WHERE email = 'ops@example.com'");
        admin = TestSupport.adminLogin(mockMvc, "opsadmin");
        member = TestSupport.member(mockMvc, auth, "hong@example.com", "홍길동");
        seeded = mockMvc.perform(get("/api/site-copy")).andReturn().getResponse().getContentAsString();
    }

    @AfterEach
    void restore() throws Exception {
        mockMvc.perform(csrf(mockMvc, put("/api/admin/site-copy"), admin).contentType(MediaType.APPLICATION_JSON).content(seeded));
    }

    @Test
    @DisplayName("심은 글이 로그인 없이 읽힌다 — 새 메인 글(V17)이 있고 예전 메인 문구는 걷혔다")
    void seededAndPublic() throws Exception {
        mockMvc.perform(get("/api/site-copy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.story.bodiesTitle").value("Various Body Types"))
                .andExpect(jsonPath("$.story.fitTitle").value("Custom Fit"))
                .andExpect(jsonPath("$.hero").doesNotExist())
                .andExpect(jsonPath("$.film").doesNotExist())
                .andExpect(jsonPath("$.process.length()").value(6))
                .andExpect(jsonPath("$.process[5].duration").value("총 5 – 6주"))
                .andExpect(jsonPath("$.shop.closedDays").value("화 · 수 휴무"));
    }

    @Test
    @DisplayName("관리자가 고치면 바로 읽히고 손님 화면을 다시 만든다. 회원은 못 고친다. 칸 수 · 빈 칸 · 길이가 틀리면 400")
    void adminSaves() throws Exception {
        String changed = seeded.replace("한 시간이면 충분합니다", "한 시간이면 됩니다");
        mockMvc.perform(csrf(mockMvc, put("/api/admin/site-copy"), member).contentType(MediaType.APPLICATION_JSON).content(changed))
                .andExpect(status().isForbidden());
        mockMvc.perform(csrf(mockMvc, put("/api/admin/site-copy"), admin).contentType(MediaType.APPLICATION_JSON).content(changed))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/site-copy")).andExpect(jsonPath("$.booking.line2").value("한 시간이면 됩니다"));
        assertThat(events.stream(FrontRebuildTrigger.SiteChanged.class).count()).isEqualTo(1);

        for (String bad : new String[] {
                // 단계 하나 빠짐
                seeded.replaceFirst("\\{\"title\":\"상담\"[^}]*},", ""),
                // 빈 제목
                seeded.replace("\"Custom Fit\"", "\"\""),
                // 너무 긴 설명 글
                seeded.replaceFirst("\"fitBody\":\"[^\"]*\"", "\"fitBody\":\"" + "가".repeat(601) + "\""),
                // 너무 긴 단계 이름
                seeded.replace("\"상담\"", "\"" + "가".repeat(21) + "\""),
                // 매장 안내 빠짐
                seeded.replaceFirst(",\"shop\":\\{[^}]*}", "")}) {
            mockMvc.perform(csrf(mockMvc, put("/api/admin/site-copy"), admin).contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isBadRequest());
        }
    }
}
