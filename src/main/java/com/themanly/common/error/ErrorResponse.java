package com.themanly.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * 모든 에러 응답의 단일 형식. 어떤 예외가 났든 이 모양으로 나간다.
 *
 * <p>{@code fieldErrors} 는 관리자 API 에만 채운다 — 공개 API 가 필드명 · 제약을 알려 주면 스키마 명세서가 된다
 * ({@link GlobalExceptionHandler}). {@code null} 이면 JSON 에서 빠진다.
 *
 * @param code    기계가 분기할 값 (예: VALIDATION_FAILED)
 * @param message 사람이 읽을 한 줄. 내부 구현을 드러내지 않는다
 * @param traceId 서버 로그와 대조할 식별자
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, String traceId, Instant timestamp,
                            List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(String code, String message, String traceId) {
        return new ErrorResponse(code, message, traceId, Instant.now(), null);
    }

    public static ErrorResponse withFields(String code, String message, String traceId, List<FieldError> fieldErrors) {
        return new ErrorResponse(code, message, traceId, Instant.now(), fieldErrors);
    }
}
