package com.themanly.common.error;

/**
 * 요청한 것이 없을 때. {@link GlobalExceptionHandler} 가 404 로 바꾼다.
 *
 * <p>"없음" 과 "볼 권한이 없음" 을 구분하지 않는다 — 남의 예약 id 를 403 으로 답하면 그 id 가 존재한다는 사실이 샌다.
 * 생성자가 받는 메시지는 <b>로그용</b>이다. 클라이언트에는 나가지 않는다.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String logMessage) {
        super(logMessage);
    }
}
