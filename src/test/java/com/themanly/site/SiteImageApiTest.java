package com.themanly.site;

import static com.themanly.TestSupport.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

/** 메인 사진 칸(V16) — 빈 칸은 안 나가고, 관리자가 걸면 로그인 없이 읽힌다. 바꾸기 · 비우기 · 없는 사진 · 회원 · 없는 칸. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
@RecordApplicationEvents
class SiteImageApiTest {

    @TempDir
    static Path store;

    @DynamicPropertySource
    static void media(DynamicPropertyRegistry registry) {
        registry.add("app.media.storage-dir", () -> store.toString());
    }

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

    /** 사진 한 장을 올리고 mediaId 를 돌려준다 */
    private String uploadImage() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(30, 20, BufferedImage.TYPE_INT_RGB), "png", out);
        var token = mockMvc.perform(get("/api/auth/csrf")).andReturn();
        String value = JsonPath.read(token.getResponse().getContentAsString(), "$.token");
        Cookie csrfCookie = token.getResponse().getCookie("XSRF-TOKEN");
        var b = multipart("/api/admin/media").file(new MockMultipartFile("file", "bodies.png", "image/png", out.toByteArray()));
        b.header("X-XSRF-TOKEN", value);
        b.cookie(admin, csrfCookie == null ? new Cookie("XSRF-TOKEN", value) : csrfCookie);
        return JsonPath.read(mockMvc.perform(b).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String body(String mediaId, String alt) {
        return "{\"mediaId\":\"%s\",\"alt\":\"%s\"}".formatted(mediaId, alt);
    }

    @Test
    @DisplayName("빈 칸은 나가지 않는다 → 걸면 로그인 없이 주소 · 대체 텍스트 · 크기가 나가고 화면을 다시 만든다 → 바꾸기 · 비우기")
    void putAndClear() throws Exception {
        mockMvc.perform(get("/api/site-images")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        String first = uploadImage();
        mockMvc.perform(csrf(mockMvc, put("/api/admin/site-images/BODIES"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body(first, "세 체형")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slot").value("BODIES"));
        mockMvc.perform(get("/api/site-images"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].mediaId").value(first))
                .andExpect(jsonPath("$[0].url").value(org.hamcrest.Matchers.startsWith("/media/")))
                .andExpect(jsonPath("$[0].alt").value("세 체형"))
                .andExpect(jsonPath("$[0].width").value(30));

        String second = uploadImage();
        mockMvc.perform(csrf(mockMvc, put("/api/admin/site-images/BODIES"), admin).contentType(MediaType.APPLICATION_JSON)
                .content(body(second, "세 체형 다시"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/site-images"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].mediaId").value(second));

        mockMvc.perform(csrf(mockMvc, delete("/api/admin/site-images/BODIES"), admin)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/site-images")).andExpect(jsonPath("$.length()").value(0));
        // 제작 순서 · 매장 칸도 같은 방식(11-02 시안)
        for (String slot : new String[] {"PROCESS", "SHOP"}) {
            mockMvc.perform(csrf(mockMvc, put("/api/admin/site-images/" + slot), admin).contentType(MediaType.APPLICATION_JSON)
                    .content(body(first, slot))).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/site-images")).andExpect(jsonPath("$[*].slot").value(org.hamcrest.Matchers.contains("PROCESS", "SHOP")));
        for (String slot : new String[] {"PROCESS", "SHOP"}) {
            mockMvc.perform(csrf(mockMvc, delete("/api/admin/site-images/" + slot), admin)).andExpect(status().isNoContent());
        }
        // 비어 있는 칸을 또 비워도 문제없다(다시 만들지 않는다)
        mockMvc.perform(csrf(mockMvc, delete("/api/admin/site-images/BODIES"), admin)).andExpect(status().isNoContent());
        assertThat(events.stream(FrontRebuildTrigger.SiteChanged.class).count()).isEqualTo(7);
    }

    @Test
    @DisplayName("회원은 못 건다(403) · 없는 사진 · 빈 대체 텍스트 · 없는 칸은 400")
    void rejects() throws Exception {
        String id = uploadImage();
        mockMvc.perform(csrf(mockMvc, put("/api/admin/site-images/SUITS"), member).contentType(MediaType.APPLICATION_JSON)
                .content(body(id, "수트"))).andExpect(status().isForbidden());
        mockMvc.perform(csrf(mockMvc, put("/api/admin/site-images/SUITS"), admin).contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID().toString(), "수트"))).andExpect(status().isBadRequest());
        mockMvc.perform(csrf(mockMvc, put("/api/admin/site-images/SUITS"), admin).contentType(MediaType.APPLICATION_JSON)
                .content(body(id, " "))).andExpect(status().isBadRequest());
        mockMvc.perform(csrf(mockMvc, put("/api/admin/site-images/HERO"), admin).contentType(MediaType.APPLICATION_JSON)
                .content(body(id, "히어로"))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/site-images")).andExpect(jsonPath("$.length()").value(0));
    }
}
