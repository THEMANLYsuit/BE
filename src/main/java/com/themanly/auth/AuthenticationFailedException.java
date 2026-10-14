package com.themanly.auth;

/**
 * 로그인 실패. 이유를 두 가지로만 나눈다 — "이메일 없음" 과 "비밀번호 틀림" 을 구분해 알려 주면
 * 어떤 이메일이 가입되어 있는지 알아낼 수 있다(계정 열거). {@link Reason#ACCOUNT_LOCKED} ·
 * {@link Reason#ACCOUNT_SUSPENDED} 는 <b>비밀번호가 맞았을 때만</b> 내보낸다.
 */
public class AuthenticationFailedException extends RuntimeException {

    public enum Reason {
        INVALID_CREDENTIALS,
        ACCOUNT_LOCKED,
        ACCOUNT_SUSPENDED
    }

    private final Reason reason;

    public AuthenticationFailedException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
