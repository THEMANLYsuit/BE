package com.themanly.auth;

import com.themanly.auth.AuthenticationFailedException.Reason;
import com.themanly.member.Member;
import com.themanly.member.MemberRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입과 로그인. 여기가 <b>평문 비밀번호가 존재하는 유일한 지점</b>이다 — 들어오자마자 해시로 바꾸고 로그에도 남기지 않는다.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptRecorder attempts;
    private final PasswordPolicy passwordPolicy;

    /**
     * 없는 계정으로 로그인했을 때 대조할 가짜 해시. BCrypt 검증은 일부러 느려서, 없는 이메일에 즉시 실패를 답하면
     * 응답 시간 차이만으로 가입 여부를 알아낼 수 있다(타이밍 기반 계정 열거). 없는 계정에도 같은 비용을 치른다.
     */
    private final String dummyHash;

    public AuthService(MemberRepository members, PasswordEncoder passwordEncoder, LoginAttemptRecorder attempts,
                       PasswordPolicy passwordPolicy) {
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.attempts = attempts;
        this.passwordPolicy = passwordPolicy;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UUID signup(String rawEmail, String rawPassword, String name, String phone) {
        String email = Member.normalizeEmail(rawEmail);
        passwordPolicy.validate(rawPassword, email);
        if (members.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        Member member = new Member(UUID.randomUUID(), email, passwordEncoder.encode(rawPassword), name,
                phone == null || phone.isBlank() ? null : phone.trim());
        // 약관 동의 · 만 14세 확인은 가입 요청 검증(AuthRequests.Signup)이 받는다. 여기서는 기록만 한다.
        member.agreeTerms(Instant.now());
        members.save(member);
        log.info("회원 가입 memberId={}", member.getId());
        return member.getId();
    }

    /*
     * 트랜잭션을 걸지 않는다. 걸면 던지는 예외에 실패 횟수 증가까지 함께 롤백된다 (LoginAttemptRecorder 주석 참고).
     */
    public Member login(String rawEmail, String rawPassword) {
        return authenticate(members.findByEmail(Member.normalizeEmail(rawEmail)), rawPassword);
    }

    /** 관리자 로그인. 아이디 또는 이메일. 관리자가 아니면 일반 실패와 같은 답 — "관리자가 아닙니다" 는 비밀번호가 맞았다는 뜻이 된다. */
    public Member adminLogin(String rawIdentifier, String rawPassword) {
        String id = rawIdentifier == null ? "" : rawIdentifier.trim().toLowerCase(Locale.ROOT);
        Optional<Member> found = id.contains("@") ? members.findByEmail(Member.normalizeEmail(id)) : members.findByLoginId(id);
        Member member = authenticate(found, rawPassword);
        if (!member.getRole().isAdmin()) {
            log.info("관리자 로그인 거절 — 관리자 아님 memberId={}", member.getId());
            throw new AuthenticationFailedException(Reason.INVALID_CREDENTIALS);
        }
        return member;
    }

    public boolean mustChangePassword(UUID memberId) {
        return members.findById(memberId).map(Member::isMustChangePassword).orElse(false);
    }

    /**
     * 순서가 중요하다. 잠금 여부를 먼저 보고 거절하지 않는다 — 비밀번호를 모르는 사람도 "잠겨 있다 = 존재한다" 를 알게 된다.
     * 비밀번호를 먼저 대조하고, 맞았을 때만 잠금 · 정지를 알린다. 잠겨 있는 동안에는 실패 횟수를 더 올리지 않는다.
     */
    private Member authenticate(Optional<Member> candidate, String rawPassword) {
        Instant now = Instant.now();
        Optional<Member> found = candidate.filter(m -> !m.isWithdrawn());
        if (found.isEmpty()) {
            passwordEncoder.matches(rawPassword, dummyHash); // 타이밍을 맞추기 위한 헛수고
            log.info("로그인 실패 reason=NO_ACCOUNT");
            throw new AuthenticationFailedException(Reason.INVALID_CREDENTIALS);
        }
        Member member = found.get();
        boolean matches = passwordEncoder.matches(rawPassword, member.getPasswordHash());
        if (!matches) {
            if (!member.isLocked(now)) {
                attempts.recordFailure(member.getId(), now);
            }
            log.info("로그인 실패 reason=WRONG_PASSWORD memberId={}", member.getId());
            throw new AuthenticationFailedException(Reason.INVALID_CREDENTIALS);
        }
        if (member.isLocked(now)) {
            log.info("로그인 거절 reason=LOCKED memberId={}", member.getId());
            throw new AuthenticationFailedException(Reason.ACCOUNT_LOCKED);
        }
        if (member.isSuspended()) {
            log.info("로그인 거절 reason=SUSPENDED memberId={}", member.getId());
            throw new AuthenticationFailedException(Reason.ACCOUNT_SUSPENDED);
        }
        attempts.recordSuccess(member.getId(), now);
        log.info("로그인 성공 memberId={}", member.getId());
        return member;
    }

    /** 이미 가입된 이메일. 409 — 메시지에 이메일을 담지 않는다. */
    public static class EmailAlreadyRegisteredException extends RuntimeException {
        public EmailAlreadyRegisteredException() {
            super("email already registered");
        }
    }
}
