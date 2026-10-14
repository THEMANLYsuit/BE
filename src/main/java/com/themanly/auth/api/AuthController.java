package com.themanly.auth.api;

import com.themanly.auth.AuthService;
import com.themanly.auth.MemberPrincipal;
import com.themanly.auth.PasswordResetService;
import com.themanly.member.Member;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 가입 · 로그인 · 로그아웃 · 비밀번호 찾기 · 내 정보. 로그인 응답에 토큰을 담지 않는다 — 인증은 세션 쿠키다. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository contextRepository;
    private final PasswordResetService passwordReset;

    public AuthController(AuthService authService, SecurityContextRepository contextRepository,
                          PasswordResetService passwordReset) {
        this.authService = authService;
        this.contextRepository = contextRepository;
        this.passwordReset = passwordReset;
    }

    /** CSRF 토큰 발급. 프론트가 로그인 · 가입 전에 한 번 받아 두고, 이후 POST 는 X-XSRF-TOKEN 헤더에 싣는다. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(@RequestAttribute(name = "_csrf") CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    /** 회원가입. 가입 직후 자동 로그인하지 않는다 — 한 단계 끊어 둔다. 생성된 id 도 돌려주지 않는다. */
    @PostMapping("/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody AuthRequests.Signup request) {
        authService.signup(request.email(), request.password(), request.name(), request.phone());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody AuthRequests.Login request, HttpServletRequest httpRequest,
                            HttpServletResponse httpResponse) {
        return startSession(authService.login(request.email(), request.password()), httpRequest, httpResponse);
    }

    @PostMapping("/admin-login")
    public MeResponse adminLogin(@Valid @RequestBody AuthRequests.AdminLogin request, HttpServletRequest httpRequest,
                                 HttpServletResponse httpResponse) {
        return startSession(authService.adminLogin(request.loginId(), request.password()), httpRequest, httpResponse);
    }

    /** 세션 고정 공격 방어 — 인증에 성공하면 기존 세션을 버리고 새로 만든다. */
    private MeResponse startSession(Member member, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        HttpSession existing = httpRequest.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        httpRequest.getSession(true);

        MemberPrincipal principal = MemberPrincipal.from(member);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, httpRequest, httpResponse);
        return MeResponse.from(principal, member.isMustChangePassword());
    }

    /** 로그아웃 — 세션을 서버에서 지운다. 이미 로그아웃 상태여도 204(멱등). */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    /** 비밀번호 찾기 — 언제나 202. 가입된 이메일이든 아니든 같은 답을 한다. */
    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody AuthRequests.PasswordResetRequest request) {
        passwordReset.request(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody AuthRequests.PasswordResetConfirm request) {
        passwordReset.reset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        MemberPrincipal principal = (MemberPrincipal) authentication.getPrincipal();
        return MeResponse.from(principal, authService.mustChangePassword(principal.getId()));
    }

    /** 로그인 응답 · 내 정보 응답. 화면이 쓰는 값만 — 한 번 내보낸 필드는 없애기 어렵다. */
    public record MeResponse(String email, String name, List<String> roles, boolean mustChangePassword) {
        static MeResponse from(MemberPrincipal principal, boolean mustChangePassword) {
            return new MeResponse(principal.getEmail(), principal.getName(),
                    principal.getAuthorities().stream().map(a -> a.getAuthority()).toList(), mustChangePassword);
        }
    }
}
