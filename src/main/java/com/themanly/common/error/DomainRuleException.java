package com.themanly.common.error;

import org.springframework.http.HttpStatus;

/**
 * 업무 규칙 위반 — 메시지가 <b>그대로 화면에 보인다.</b>
 *
 * <p>"이미 예약이 있는 시간입니다", "지난 예약은 취소할 수 없습니다" 처럼 손님 · 관리자가 다음에 무엇을 해야 하는지
 * 알아야 하는 경우에 쓴다. 그래서 메시지에 내부 식별자 · 경로 · 라이브러리 이름을 넣지 않는다.
 *
 * <p>기본은 409(지금 상태에서 할 수 없음). 입력 자체가 틀린 경우는 {@link #badRequest} 로 400.
 */
public class DomainRuleException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public DomainRuleException(String code, String message) {
        this(code, message, HttpStatus.CONFLICT);
    }

    public DomainRuleException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public static DomainRuleException badRequest(String code, String message) {
        return new DomainRuleException(code, message, HttpStatus.BAD_REQUEST);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
