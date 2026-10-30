package com.themanly.catalog;

import static com.themanly.TestSupport.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.themanly.site.FrontRebuildTrigger;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

/** 원단 · 스타일 카탈로그 — 관리자 등록과 손님 노출 규칙. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class})
@RecordApplicationEvents
class CatalogApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuthService auth;
    @Autowired
    private AdminMemberService admins;

    private Cookie admin;

    @Autowired
    private ApplicationEvents events;

    /** 손님 화면 다시 빌드 요청 수 (FrontRebuildTrigger.SiteChanged) */
    private long rebuilds() {
        return events.stream(FrontRebuildTrigger.SiteChanged.class).count();
    }

    @BeforeEach
    void setUp() throws Exception {
        TestSupport.cleanDatabase(jdbc);
        admins.createWithLoginId("opsadmin", "ops@example.com", "운영자", MemberRole.ADMIN, TestSupport.PASSWORD);
        // 임시 비밀번호 표시가 있으면 관리자 API 가 막힌다 — 바꾼 것으로 본다
        jdbc.update("UPDATE member SET must_change_password = FALSE WHERE email = 'ops@example.com'");
        auth.signup("seed@example.com", TestSupport.PASSWORD, "씨앗", null);
        admin = TestSupport.adminLogin(mockMvc, "opsadmin");
    }

    private String fabric(String code, String name) {
        return """
                {"code":"%s","name":"%s","mill":"VBC","composition":"울 100%%","weightGsm":260,"color":"네이비","pattern":"솔리드",
                 "season":"ALL","priceFromKrw":1200000,"description":"사계절 네이비.","imageUrl":""}
                """.formatted(code, name);
    }

    @Test
    @DisplayName("원단 — 등록하면 준비 중(손님에게 안 보임), 공개하면 보이고, 품절은 보이되 soldOut. 코드는 대문자로, 겹치면 409")
    void fabricLifecycle() throws Exception {
        String res = mockMvc.perform(csrf(mockMvc, post("/api/admin/fabrics"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(fabric("vbc-1021", "네이비 트로피컬")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("VBC-1021"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(res, "$.id");

        mockMvc.perform(get("/api/fabrics")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/fabrics/VBC-1021")).andExpect(status().isNotFound());

        mockMvc.perform(csrf(mockMvc, put("/api/admin/fabrics/" + id + "/status"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}")).andExpect(status().isOk());
        mockMvc.perform(get("/api/fabrics/vbc-1021"))
                .andExpect(jsonPath("$.name").value("네이비 트로피컬"))
                .andExpect(jsonPath("$.priceFromKrw").value(1200000))
                .andExpect(jsonPath("$.soldOut").value(false));

        mockMvc.perform(csrf(mockMvc, put("/api/admin/fabrics/" + id + "/status"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SOLD_OUT\"}")).andExpect(status().isOk());
        mockMvc.perform(get("/api/fabrics")).andExpect(jsonPath("$[0].soldOut").value(true));

        mockMvc.perform(csrf(mockMvc, post("/api/admin/fabrics"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(fabric("VBC-1021", "다른 이름")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
        // 관리자 API 는 필드별 오류를 준다
        mockMvc.perform(csrf(mockMvc, post("/api/admin/fabrics"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(fabric("x", "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @DisplayName("스타일 선택지 — 묶음마다 기본값은 하나. 새 기본값이 생기면 옛 기본값이 내려간다. 꺼 둔 선택지는 손님에게 안 나간다")
    void styleOptions() throws Exception {
        String notch = """
                {"category":"LAPEL","code":"notch","name":"노치 라펠","description":"","imageUrl":"","isDefault":true,"enabled":true}
                """;
        String peak = """
                {"category":"LAPEL","code":"peak","name":"피크 라펠","description":"","imageUrl":"","isDefault":true,"enabled":true}
                """;
        String shawl = """
                {"category":"LAPEL","code":"shawl","name":"숄 칼라","description":"","imageUrl":"","isDefault":false,"enabled":false}
                """;
        mockMvc.perform(csrf(mockMvc, post("/api/admin/style-options"), admin).contentType(MediaType.APPLICATION_JSON).content(notch))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isDefault").value(true));
        mockMvc.perform(csrf(mockMvc, post("/api/admin/style-options"), admin).contentType(MediaType.APPLICATION_JSON).content(peak))
                .andExpect(status().isCreated());
        mockMvc.perform(csrf(mockMvc, post("/api/admin/style-options"), admin).contentType(MediaType.APPLICATION_JSON).content(shawl))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/style-options"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("notch")).andExpect(jsonPath("$[0].isDefault").value(false))
                .andExpect(jsonPath("$[1].code").value("peak")).andExpect(jsonPath("$[1].isDefault").value(true))
                .andExpect(jsonPath("$[1].categoryLabel").value("라펠"));

        mockMvc.perform(csrf(mockMvc, post("/api/admin/style-options"), admin).contentType(MediaType.APPLICATION_JSON).content(peak))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("원단 순서 — 관리자가 준 id 순서대로 손님 목록이 바뀐다. 모르는 id 가 섞이면 400")
    void reorderFabrics() throws Exception {
        String a = JsonPath.read(mockMvc.perform(csrf(mockMvc, post("/api/admin/fabrics"), admin).contentType(MediaType.APPLICATION_JSON)
                .content(fabric("A-1", "첫째"))).andReturn().getResponse().getContentAsString(), "$.id");
        String b = JsonPath.read(mockMvc.perform(csrf(mockMvc, post("/api/admin/fabrics"), admin).contentType(MediaType.APPLICATION_JSON)
                .content(fabric("B-1", "둘째"))).andReturn().getResponse().getContentAsString(), "$.id");
        for (String id : new String[] {a, b}) {
            mockMvc.perform(csrf(mockMvc, put("/api/admin/fabrics/" + id + "/status"), admin).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"PUBLISHED\"}")).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/fabrics")).andExpect(jsonPath("$[0].name").value("첫째"));
        mockMvc.perform(csrf(mockMvc, put("/api/admin/fabrics/order"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"" + b + "\",\"" + a + "\"]}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/fabrics")).andExpect(jsonPath("$[0].name").value("둘째")).andExpect(jsonPath("$[1].name").value("첫째"));
        mockMvc.perform(csrf(mockMvc, put("/api/admin/fabrics/order"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"" + java.util.UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("손님은 카탈로그를 고칠 수 없다 — 로그인하지 않으면 401, 손님이면 403")
    void customerCannotEdit() throws Exception {
        mockMvc.perform(csrf(mockMvc, post("/api/admin/fabrics")).contentType(MediaType.APPLICATION_JSON).content(fabric("A-1", "a")))
                .andExpect(status().isUnauthorized());
        Cookie member = TestSupport.login(mockMvc, "seed@example.com");
        mockMvc.perform(csrf(mockMvc, post("/api/admin/fabrics"), member).contentType(MediaType.APPLICATION_JSON).content(fabric("A-1", "a")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("손님 화면 다시 빌드 — 준비 중 원단 등록 · 수정은 안 하고, 공개 · 공개 원단 수정 · 순서 · 스타일 선택지는 한다")
    void rebuildOnlyWhenCustomersSeeIt() throws Exception {
        String res = mockMvc.perform(csrf(mockMvc, post("/api/admin/fabrics"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(fabric("VBC-2001", "차콜 플란넬")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(res, "$.id");
        mockMvc.perform(csrf(mockMvc, put("/api/admin/fabrics/" + id), admin).contentType(MediaType.APPLICATION_JSON)
                .content(fabric("VBC-2001", "차콜 플란넬 2"))).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(rebuilds()).isZero();

        mockMvc.perform(csrf(mockMvc, put("/api/admin/fabrics/" + id + "/status"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PUBLISHED\"}")).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(rebuilds()).isEqualTo(1);
        mockMvc.perform(csrf(mockMvc, put("/api/admin/fabrics/" + id), admin).contentType(MediaType.APPLICATION_JSON)
                .content(fabric("VBC-2001", "차콜 플란넬 3"))).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(rebuilds()).isEqualTo(2);
        mockMvc.perform(csrf(mockMvc, put("/api/admin/fabrics/order"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + id + "\"]}")).andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(rebuilds()).isEqualTo(3);

        mockMvc.perform(csrf(mockMvc, post("/api/admin/style-options"), admin).contentType(MediaType.APPLICATION_JSON).content("""
                {"category":"LAPEL","code":"notch","name":"노치 라펠","description":"","imageUrl":"","isDefault":true,"enabled":true}
                """)).andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(rebuilds()).isEqualTo(4);
        // 꺼 둔 선택지 등록은 손님 화면과 상관없다
        mockMvc.perform(csrf(mockMvc, post("/api/admin/style-options"), admin).contentType(MediaType.APPLICATION_JSON).content("""
                {"category":"LAPEL","code":"shawl","name":"숄 칼라","description":"","imageUrl":"","isDefault":false,"enabled":false}
                """)).andExpect(status().isCreated());
        org.assertj.core.api.Assertions.assertThat(rebuilds()).isEqualTo(4);
    }
}
