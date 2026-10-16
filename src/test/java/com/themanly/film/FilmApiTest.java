package com.themanly.film;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * 메인 스크롤 필름 (V11) — 영상을 올리면 ffmpeg 가 사진 묶음을 만들고 메인에 건다. 새로 올리면 바뀐다.
 * ffmpeg 가 없는 곳(CI 등)에서는 변환 테스트를 건너뛴다 — 권한 · 형식 검사는 그대로 돈다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
class FilmApiTest {

    @TempDir
    static Path store;

    private static boolean ffmpeg;
    private static byte[] clip;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.media.storage-dir", () -> store.toString());
        registry.add("app.api.base-url", () -> "https://api.example.test");
    }

    /** 2초짜리 시험 영상을 ffmpeg 로 만든다(색 막대). ffmpeg 가 없으면 변환 테스트를 건너뛴다 */
    @BeforeAll
    static void makeClip() throws Exception {
        Path out = store.resolve("clip.mp4");
        try {
            Process p = new ProcessBuilder("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc=size=320x240:rate=24:duration=2",
                    "-pix_fmt", "yuv420p", out.toString()).redirectErrorStream(true).start();
            ffmpeg = p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0 && Files.size(out) > 0;
            if (ffmpeg) {
                clip = Files.readAllBytes(out);
            }
        } catch (java.io.IOException e) {
            ffmpeg = false;
        }
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

    private MockMultipartHttpServletRequestBuilder upload(byte[] bytes, String name, Cookie who) throws Exception {
        var token = mockMvc.perform(get("/api/auth/csrf")).andReturn();
        String value = JsonPath.read(token.getResponse().getContentAsString(), "$.token");
        Cookie csrfCookie = token.getResponse().getCookie("XSRF-TOKEN");
        var b = multipart("/api/admin/film").file(new MockMultipartFile("file", name, "video/mp4", bytes));
        b.header("X-XSRF-TOKEN", value);
        b.cookie(who, csrfCookie == null ? new Cookie("XSRF-TOKEN", value) : csrfCookie);
        return b;
    }

    @Test
    @DisplayName("영상을 올리면 1초 12장 사진 묶음이 생기고 메인에 걸린다 — 주소 규칙 · 사진 서빙 · 새로 올리면 교체")
    void uploadAndServe() throws Exception {
        assumeTrue(ffmpeg, "ffmpeg 가 없어 변환 테스트를 건너뜀");
        mockMvc.perform(get("/api/film")).andExpect(status().isNoContent());

        String res = mockMvc.perform(upload(clip, "turn.mp4", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.fps").value(12))
                .andReturn().getResponse().getContentAsString();
        int frames = JsonPath.read(res, "$.frames");
        assertThat(frames).isBetween(22, 26); // 2초 × 12장
        String id = JsonPath.read(res, "$.id");

        mockMvc.perform(get("/api/film"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.base").value("https://api.example.test/film/" + id + "/f"))
                .andExpect(jsonPath("$.frames").value(frames));
        mockMvc.perform(get("/film/" + id + "/f001.webp"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/webp"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")));
        assertThat(Files.exists(store.resolve("film").resolve(id).resolve("source.tmp"))).isFalse(); // 원본은 남기지 않는다

        String second = JsonPath.read(mockMvc.perform(upload(clip, "turn2.mp4", admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get("/api/film")).andExpect(jsonPath("$.id").value(second));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM film WHERE active", Long.class)).isEqualTo(1);
        mockMvc.perform(get("/api/admin/film").cookie(admin)).andExpect(jsonPath("$.recent.length()").value(2));
    }

    @Test
    @DisplayName("영상이 아닌 파일 · 회원 · 이상한 사진 이름은 막는다")
    void guards() throws Exception {
        mockMvc.perform(upload("<html>not a video</html>".getBytes(StandardCharsets.UTF_8), "x.mp4", admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VIDEO_INVALID"));
        mockMvc.perform(upload(new byte[] {0, 0, 0, 0x18, 'f', 't', 'y', 'p'}, "x.mp4", member)).andExpect(status().isForbidden());
        mockMvc.perform(get("/film/" + java.util.UUID.randomUUID() + "/source.tmp")).andExpect(status().isNotFound());
        mockMvc.perform(get("/film/" + java.util.UUID.randomUUID() + "/f001.webp")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM film", Long.class)).isZero();
    }
}
