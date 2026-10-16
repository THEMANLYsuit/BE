package com.themanly.admin;

import com.themanly.auth.MemberPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 관리자 API({@code /api/admin/**}) 요청마다 접속 기록을 한 줄 남긴다 (V10).
 *
 * <p>응답이 끝난 뒤(status 가 정해진 뒤) 적는다. 이 필터는 보안 필터 체인 <b>뒤</b>에 있어서, 보안이 거절한 요청(401 · 403 —
 * 비로그인 · 권한 없는 회원)은 여기까지 오지 않고 남지 않는다. 그건 일반 요청 로그가 본다. 관리자가 실제로 통과한 요청은
 * 결과가 404 · 409 · 500 이어도 남는다. 쿼리스트링 · 본문은 적지 않는다(개인정보).
 * 기록 저장이 실패해도 요청은 성공한 채로 둔다 — 로그 때문에 주문 처리가 멈추면 안 된다. 대신 서버 로그에 error 로 남긴다.
 *
 * <p>IP 는 앞단(Caddy)이 붙이는 X-Forwarded-For 의 첫 값. 없으면 소켓 주소.
 */
@Component
public class AdminAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminAccessLogFilter.class);

    private final AdminAccessLogRepository logs;

    public AdminAccessLogFilter(AdminAccessLogRepository logs) {
        this.logs = logs;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/admin/") || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            record(request, response);
        }
    }

    private void record(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof MemberPrincipal me)) {
            return;
        }
        try {
            logs.save(new AdminAccessLog(me.getId(), me.getEmail(), request.getMethod(), request.getRequestURI(),
                    response.getStatus(), clientIp(request), request.getHeader("User-Agent")));
        } catch (RuntimeException e) {
            log.error("관리자 접속 기록 저장 실패 {} {} by={}", request.getMethod(), request.getRequestURI(), me.getEmail(), e);
        }
    }

    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}