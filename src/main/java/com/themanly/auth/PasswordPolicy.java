package com.themanly.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** 비밀번호 규칙 — 한 곳에서만 판단한다. 10자 이상, BCrypt 한계(72바이트) 이내, 이메일 아이디 부분을 포함하지 않음. */
@Component
public class PasswordPolicy {

    static final int MIN_LENGTH = 10;
    static final int MAX_BYTES = 72;

    public void validate(String rawPassword, String email) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new WeakPasswordException("비밀번호는 " + MIN_LENGTH + "자 이상이어야 합니다.");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new WeakPasswordException("비밀번호가 너무 깁니다. 영문 기준 " + MAX_BYTES + "자 이내로 입력해 주세요.");
        }
        int at = email.indexOf('@');
        String localPart = at < 0 ? email : email.substring(0, at);
        if (!localPart.isBlank() && rawPassword.toLowerCase(Locale.ROOT).contains(localPart.toLowerCase(Locale.ROOT))) {
            throw new WeakPasswordException("비밀번호에 이메일 주소를 포함할 수 없습니다.");
        }
    }

    /** 규칙 위반. 메시지가 그대로 화면에 보인다 — 규칙은 공개된 정보다. */
    public static class WeakPasswordException extends RuntimeException {
        public WeakPasswordException(String message) {
            super(message);
        }
    }
}
