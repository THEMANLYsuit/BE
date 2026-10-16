package com.themanly.media;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 사진 올리기 · 내보내기 (V9) — 형식 판별, 위장 파일 거부, nosniff, 권한. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
class MediaApiTest {

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

    /** 멀티파트 요청은 TestSupport.csrf(일반 요청 빌더)에 들어가지 않는다 — 같은 일을 여기서 한다. */
    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder upload(
            MockMultipartFile file, Cookie who) throws Exception {
        var token = mockMvc.perform(get("/api/auth/csrf")).andReturn();
        String value = JsonPath.read(token.getResponse().getContentAsString(), "$.token");
        Cookie csrfCookie = token.getResponse().getCookie("XSRF-TOKEN");
        var b = multipart("/api/admin/media").file(file);
        b.header("X-XSRF-TOKEN", value);
        b.cookie(who, csrfCookie == null ? new Cookie("XSRF-TOKEN", value) : csrfCookie);
        return b;
    }

    private static byte[] png(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("PNG 를 올리면 크기를 재고 주소를 준다. 그 주소는 누구나 열 수 있고, 판별한 타입 · nosniff 로 나간다")
    void uploadAndServe() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "navy/../원단.png", "application/octet-stream", png(3, 2));
        String res = mockMvc.perform(upload(file, admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.width").value(3))
                .andExpect(jsonPath("$.height").value(2))
                .andExpect(jsonPath("$.filename").value("navy..원단.png"))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(res, "$.url");

        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("이미지로 위장한 HTML 은 받지 않는다 — 선언한 타입 · 확장자를 믿지 않는다")
    void disguisedFileRejected() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("file", "photo.png", "image/png",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(upload(fake, admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT count(*) FROM media_asset", Long.class)).isZero();
    }

    @Test
    @DisplayName("올리기는 관리자만. 기록에 없는 키 · 저장소 밖으로 나가는 경로는 거부")
    void permissionsAndUnknown() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", png(1, 1));
        mockMvc.perform(upload(file, member)).andExpect(status().isForbidden());
        mockMvc.perform(get("/media/2026-10-15/none.png")).andExpect(status().isNotFound());
        // 인코딩한 ../ 는 보안 필터(방화벽)가 컨트롤러에 닿기 전에 400 으로 거부한다 — 어느 쪽이든 파일은 나가지 않는다
        mockMvc.perform(get("/media/..%2F..%2Fbuild.gradle")).andExpect(status().is4xxClientError());
    }
}
