package com.themanly.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Component;

/** 어떤 회원의 세션을 서버에서 끊는다 — 비밀번호 변경 · 역할 변경 · 정지 · 탈퇴 때. 세션은 이메일(principal)로 찾는다. */
@Component
public class SessionTerminator {

    private static final Logger log = LoggerFactory.getLogger(SessionTerminator.class);

    private final FindByIndexNameSessionRepository<?> sessions;

    public SessionTerminator(FindByIndexNameSessionRepository<?> sessions) {
        this.sessions = sessions;
    }

    public int terminateAll(String email) {
        return terminate(email, null);
    }

    /** 지금 세션은 남기고 다른 기기만 끊는다 (비밀번호 변경). */
    public int terminateOthers(String email, String keepSessionId) {
        return terminate(email, keepSessionId);
    }

    private int terminate(String email, String keepSessionId) {
        int count = 0;
        for (String id : sessions.findByPrincipalName(email).keySet()) {
            if (!id.equals(keepSessionId)) {
                sessions.deleteById(id);
                count++;
            }
        }
        log.info("세션 종료 count={}", count);
        return count;
    }
}
