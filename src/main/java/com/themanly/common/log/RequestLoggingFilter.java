package com.themanly.common.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 한 건당 한 줄씩 로그를 남긴다.
 *
 * <p><b>개인정보를 로그에 남기지 않는다.</b> 쿼리스트링 · 본문 · 헤더(쿠키)는 기록하지 않는다.
 * 남기는 것은 메서드 · 경로 · 상태코드 · 소요시간 · traceId 뿐이다.
 *
 * <p>traceId 는 여기서 만들어 {@link MDC} 에 넣는다. 같은 요청의 모든 로그와 에러 응답이 같은 id 를 공유한다.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    /** 헬스체크는 수십 초마다 들어와서 로그를 덮어버린다. */
    private static final String HEALTH_PATH = "/actuator/health";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(HEALTH_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            // getRequestURI() 는 쿼리스트링을 포함하지 않는다 — 개인정보가 섞이지 않는다.
            log.info("{} {} -> {} ({}ms) traceId={}", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), elapsedMs, traceId);
            // 스레드가 재사용되므로 반드시 지운다.
            MDC.remove(TRACE_ID);
        }
    }
}
