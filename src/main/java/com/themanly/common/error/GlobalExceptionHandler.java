package com.themanly.common.error;

import com.themanly.auth.AuthService;
import com.themanly.auth.AuthenticationFailedException;
import com.themanly.auth.PasswordPolicy;
import com.themanly.auth.PasswordResetService;
import com.themanly.common.log.RequestLoggingFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 모든 예외를 {@link ErrorResponse} 하나의 형식으로 바꾼다.
 *
 * <p>필드별 검증 메시지는 관리자 API({@code /api/admin/**})에만 내보낸다. 공개 API 는 뭉뚱그린 한 줄만 주고
 * 상세는 서버 로그에만 남긴다 — 필드명 · 제약조건이 그대로 나가면 공격자에게 스키마 명세서가 된다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String ADMIN_PATH_PREFIX = "/api/admin";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String traceId = currentTraceId();
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        log.warn("검증 실패 traceId={} path={} fields={}", traceId, request.getRequestURI(), fieldErrors);
        return validationResponse(request, traceId, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        String traceId = currentTraceId();
        List<ErrorResponse.FieldError> fieldErrors = e.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        log.warn("제약 위반 traceId={} path={} violations={}", traceId, request.getRequestURI(), fieldErrors);
        return validationResponse(request, traceId, fieldErrors);
    }

    private ResponseEntity<ErrorResponse> validationResponse(HttpServletRequest request, String traceId,
                                                             List<ErrorResponse.FieldError> fieldErrors) {
        if (request.getRequestURI().startsWith(ADMIN_PATH_PREFIX)) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.withFields("VALIDATION_FAILED", "입력값을 확인해 주세요.", traceId, fieldErrors));
        }
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_FAILED", "입력값을 확인해 주세요.", traceId));
    }

    /** JSON 이 깨졌거나 타입이 맞지 않는다. 파싱 예외 메시지는 내부 클래스명을 담고 있어 내보내지 않는다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e, HttpServletRequest request) {
        String traceId = currentTraceId();
        log.warn("요청 본문 해석 실패 traceId={} path={} reason={}", traceId, request.getRequestURI(),
                e.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.of("MALFORMED_REQUEST", "요청 형식이 올바르지 않습니다.", traceId));
    }

    /** 쿼리 파라미터 · 경로변수 타입 불일치 (예: 날짜 자리에 글자). 받은 값은 되비추지 않는다 — 로그에만. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String traceId = currentTraceId();
        log.warn("파라미터 타입 불일치 traceId={} path={} name={} value={}", traceId, request.getRequestURI(),
                e.getName(), e.getValue());
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_PARAMETER", "요청 값이 올바르지 않습니다.", traceId));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.", currentTraceId()));
    }

    /** 도메인이 "없다" 고 판단한 경우. 메시지는 로그에만 남긴다. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException e, HttpServletRequest request) {
        String traceId = currentTraceId();
        log.info("리소스 없음 traceId={} path={} reason={}", traceId, request.getRequestURI(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.", traceId));
    }

    /** 로그인 실패. 401. INVALID_CREDENTIALS 하나가 "없는 이메일 · 틀린 비밀번호 · 탈퇴한 계정" 을 모두 덮는다. */
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ErrorResponse> handleAuthFailed(AuthenticationFailedException e) {
        String message = switch (e.getReason()) {
            case INVALID_CREDENTIALS -> "이메일 또는 비밀번호가 올바르지 않습니다.";
            case ACCOUNT_LOCKED -> "로그인 시도가 많아 계정이 잠겼습니다. 잠시 후 다시 시도해 주세요.";
            case ACCOUNT_SUSPENDED -> "이용이 정지된 계정입니다. 고객센터로 문의해 주세요.";
        };
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(e.getReason().name(), message, currentTraceId()));
    }

    /** 이미 가입된 이메일. 409. 입력값을 되비추지 않는다. */
    @ExceptionHandler(AuthService.EmailAlreadyRegisteredException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(AuthService.EmailAlreadyRegisteredException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다.", currentTraceId()));
    }

    /** 비밀번호 규칙 위반. 400. 규칙은 공개된 정보라 메시지를 그대로 내보낸다. */
    @ExceptionHandler(PasswordPolicy.WeakPasswordException.class)
    public ResponseEntity<ErrorResponse> handleWeakPassword(PasswordPolicy.WeakPasswordException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("WEAK_PASSWORD", e.getMessage(), currentTraceId()));
    }

    /** 비밀번호 설정 링크가 없거나 · 만료됐거나 · 이미 쓰였다. 셋을 구분하지 않는다. 400. */
    @ExceptionHandler(PasswordResetService.InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidResetToken(PasswordResetService.InvalidTokenException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_RESET_TOKEN",
                "링크가 만료되었거나 이미 사용되었습니다. 비밀번호 찾기를 다시 해 주세요.", currentTraceId()));
    }

    /** 업무 규칙 위반 — 메시지가 그대로 화면에 보인다 ({@link DomainRuleException}). */
    @ExceptionHandler(DomainRuleException.class)
    public ResponseEntity<ErrorResponse> handleDomainRule(DomainRuleException e, HttpServletRequest request) {
        String traceId = currentTraceId();
        log.info("규칙 위반 traceId={} path={} code={}", traceId, request.getRequestURI(), e.getCode());
        return ResponseEntity.status(e.getStatus()).body(ErrorResponse.of(e.getCode(), e.getMessage(), traceId));
    }

    /** DB 유니크에 걸렸다 — 같은 자리를 동시에 누른 경우 등. 409. */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(org.springframework.dao.DataIntegrityViolationException e, HttpServletRequest request) {
        String traceId = currentTraceId();
        log.warn("무결성 위반 traceId={} path={} reason={}", traceId, request.getRequestURI(), e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("DUPLICATE", "같은 값이 방금 다른 곳에서 저장되었습니다. 화면을 새로고침해 주세요.", traceId));
    }

    /** 상태 전이 규칙 위반(엔티티가 던진다). 409. 메시지에 내부 식별자가 섞일 수 있어 그대로 내보내지 않는다. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e, HttpServletRequest request) {
        String traceId = currentTraceId();
        log.warn("상태 규칙 위반 traceId={} path={} reason={}", traceId, request.getRequestURI(), e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("INVALID_STATE", "지금 상태에서는 할 수 없는 작업입니다. 화면을 새로고침해 주세요.", traceId));
    }

    /** 올린 파일이 서블릿 상한(max-file-size)을 넘었다 (V9 사진). 다 받기 전에 끊긴다. 400. */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("FILE_TOO_LARGE", "파일이 너무 큽니다. 10MB 이하로 올려 주세요.", currentTraceId()));
    }

    /** 위에서 걸리지 않은 모든 예외. 예외 메시지를 절대 내보내지 않는다 — traceId 로 서버 로그를 찾는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        String traceId = currentTraceId();
        log.error("처리되지 않은 예외 traceId={} path={}", traceId, request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", traceId));
    }

    /** {@link RequestLoggingFilter} 가 MDC 에 넣어 둔 traceId. 필터를 타지 않는 경로에서만 새로 만든다. */
    private String currentTraceId() {
        String fromMdc = MDC.get(RequestLoggingFilter.TRACE_ID);
        return fromMdc != null ? fromMdc : UUID.randomUUID().toString().substring(0, 8);
    }
}
