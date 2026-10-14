package com.themanly.auth;

import com.themanly.member.MemberRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 시도 결과를 <b>독립된 트랜잭션</b>으로 기록한다.
 *
 * <p>로그인 메서드 안에서 실패 횟수를 올리고 예외를 던지면 트랜잭션이 롤백되어 횟수가 사라진다 — 계정 잠금이 이름만 남는다.
 * 같은 클래스 안의 호출은 프록시를 타지 않아 전파 설정이 무시되므로 별도 빈이어야 한다.
 */
@Component
public class LoginAttemptRecorder {

    private final MemberRepository members;

    public LoginAttemptRecorder(MemberRepository members) {
        this.members = members;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID memberId, Instant now) {
        members.findById(memberId).ifPresent(m -> m.recordFailedLogin(now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID memberId, Instant now) {
        members.findById(memberId).ifPresent(m -> m.recordSuccessfulLogin(now));
    }
}
