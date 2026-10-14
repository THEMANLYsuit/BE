package com.themanly.auth;

import com.themanly.common.error.DomainRuleException;
import com.themanly.member.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 임시 비밀번호 관리자는 비밀번호를 바꾸기 전까지 관리자 API 를 쓸 수 없다.
 * 세션에는 그 표시가 없으므로(바뀌면 세션이 낡는다) 요청마다 DB 에서 본다 — 관리자 요청에만, 한 번의 PK 조회다.
 */
@Component
public class AdminPasswordGate implements HandlerInterceptor, WebMvcConfigurer {

    private final MemberRepository members;

    public AdminPasswordGate(MemberRepository members) {
        this.members = members;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/admin/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof MemberPrincipal principal) {
            boolean mustChange = members.findById(principal.getId()).map(m -> m.isMustChangePassword()).orElse(false);
            if (mustChange) {
                throw new DomainRuleException("PASSWORD_CHANGE_REQUIRED", "임시 비밀번호입니다. 비밀번호를 바꾼 뒤 이용해 주세요.",
                        HttpStatus.FORBIDDEN);
            }
        }
        return true;
    }
}
