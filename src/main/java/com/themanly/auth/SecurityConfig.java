package com.themanly.auth;

import com.themanly.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
// Jackson 3 부터 databind 패키지가 tools.jackson 으로 옮겨졌다.
import tools.jackson.databind.ObjectMapper;

/**
 * 보안 설정. 인증은 <b>세션 쿠키</b>(HttpOnly · SameSite=Lax · DB 저장)다 — 토큰을 localStorage 에 두지 않는다.
 * 화면과 API 가 같은 상위 도메인 아래 있어야 한다(예: themanly.kr · api.themanly.kr).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** BCrypt 를 위임형으로 감싼다 — 해시에 {bcrypt} 접두사가 붙어 나중에 알고리즘을 옮길 수 있다. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * 세션 쿠키. 보안 속성은 설정 파일이 아니라 여기서 못박는다 — Spring Session 에는 server.servlet.session.cookie.* 가
     * 전달되지 않는다(레오네페리토에서 확인). Secure 만 설정에서 읽는다(로컬은 http).
     */
    @Bean
    public CookieSerializer cookieSerializer(@Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("TMSESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(secure);
        // base64 를 끈다 — 아무 쿠키나 풀면 NUL 바이트가 나와 PostgreSQL 조회가 500 이 난다. 세션 id 는 UUID 다.
        serializer.setUseBase64Encoding(false);
        return serializer;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SecurityContextRepository contextRepository,
                                           ObjectMapper objectMapper) throws Exception {
        // CSRF 토큰을 쿠키(XSRF-TOKEN)로 내려 주고 헤더(X-XSRF-TOKEN)로 돌려받는다. 매 요청 즉시 해석해야 쿠키가 실제로 내려간다.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler))
                // JSON API 다 — 폼 로그인 · Basic · 기본 로그아웃을 쓰지 않는다
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                // 세션은 필요할 때만 — 원단 목록을 보기만 해도 세션 행이 생기지 않게
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        // 공개 — 손님 화면이 빌드 때 · 둘러볼 때 받는다
                        .requestMatchers(HttpMethod.GET, "/api/fabrics", "/api/fabrics/*", "/api/style-options").permitAll()
                        // 빈 시간은 로그인 없이 본다 — 예약 자체는 로그인해야 한다
                        .requestMatchers(HttpMethod.GET, "/api/bookings/availability").permitAll()
                        // 문의 보내기 — 비회원도. 로그인했으면 회원에 묶인다(컨트롤러가 principal 을 본다)
                        .requestMatchers(HttpMethod.POST, "/api/inquiries").permitAll()
                        // 올린 사진 내보내기 (V9). 올리기는 /api/admin/media — 아래 관리자 규칙
                        .requestMatchers(HttpMethod.GET, "/media/**").permitAll()
                        .requestMatchers("/api/auth/signup", "/api/auth/login", "/api/auth/admin-login", "/api/auth/csrf",
                                "/api/auth/password-reset/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // 관리자 지정 · 해제는 최고 관리자만. /api/admin/** 보다 먼저 와야 한다
                        .requestMatchers(HttpMethod.PUT, "/api/admin/members/*/role").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 나머지는 전부 인증 — 새 엔드포인트를 만들고 규칙을 깜빡해도 열리지 않는다
                        .anyRequest().authenticated())
                // 인증이 없을 때 302 가 아니라 401 JSON
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다."))
                        .accessDeniedHandler((request, response, e) ->
                                writeError(response, objectMapper, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "권한이 없습니다.")));

        http.securityContext(context -> context.securityContextRepository(contextRepository));
        return http.build();
    }

    private void writeError(HttpServletResponse response, ObjectMapper objectMapper, int status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code, message, null));
    }

    /** CORS. 쿠키를 주고받으므로 * 를 쓸 수 없다 — 허용 출처를 설정으로 받는다(기본은 로컬 FE 뿐). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:3001}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
