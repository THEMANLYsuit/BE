package com.themanly.auth.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 인증 요청 본문. 비밀번호 규칙은 {@code PasswordPolicy} 한 곳에서 판단한다 —
 * 여기 상한(1000자)은 해시 계산 전에 터무니없이 큰 입력을 끊기 위한 방어선이다.
 */
public final class AuthRequests {

    private AuthRequests() {
    }

    public record Signup(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 1000) String password,
            @NotBlank @Size(max = 50) String name,
            @Pattern(regexp = "^[0-9-]{9,20}$", message = "전화번호 형식이 올바르지 않습니다.") String phone,
            // 약관 동의 · 만 14세 확인. 화면의 체크만 믿지 않고 서버가 다시 본다.
            @AssertTrue(message = "이용약관에 동의해 주세요.") boolean agreeTerms,
            @AssertTrue(message = "만 14세 이상만 가입할 수 있습니다.") boolean over14) {
    }

    public record Login(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 1000) String password) {
    }

    /** 관리자 로그인 — 아이디(login_id) 또는 이메일. */
    public record AdminLogin(@NotBlank @Size(max = 254) String loginId, @NotBlank @Size(max = 1000) String password) {
    }

    /** 아이디 찾기 — 가입할 때(또는 마이페이지에서) 적은 이름과 휴대폰 번호. 하이픈은 있어도 없어도 된다. */
    public record FindEmail(
            @NotBlank @Size(max = 50) String name,
            @NotBlank @Pattern(regexp = "^[0-9-]{9,20}$", message = "휴대폰 번호 형식이 올바르지 않습니다.") String phone) {
    }

    public record PasswordResetRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record PasswordResetConfirm(@NotBlank @Size(max = 100) String token, @NotBlank @Size(max = 1000) String newPassword) {
    }
}
