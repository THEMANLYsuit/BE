package com.themanly.auth;

import com.themanly.mail.Mailer;
import com.themanly.member.Member;
import com.themanly.member.MemberRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 설정 · 재설정 링크.
 *
 * <ul>
 *   <li>{@link #issueLink} — 관리자 생성 명령이 터미널에 찍는 링크(메일 없이).</li>
 *   <li>{@link #request} — 비밀번호 찾기. 가입 여부와 관계없이 같은 답을 한다(계정 열거 방지). 가입된 주소면 메일을 보낸다.</li>
 *   <li>{@link #reset} — 링크의 토큰으로 새 비밀번호 저장. 그 계정의 모든 세션을 끊는다.</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberRepository members;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SessionTerminator sessionTerminator;
    private final Mailer mailer;
    private final String frontBaseUrl;

    public PasswordResetService(MemberRepository members, PasswordResetTokenRepository tokens,
                                PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                                SessionTerminator sessionTerminator, Mailer mailer,
                                @Value("${app.front.base-url}") String frontBaseUrl) {
        this.members = members;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.sessionTerminator = sessionTerminator;
        this.mailer = mailer;
        this.frontBaseUrl = frontBaseUrl.endsWith("/") ? frontBaseUrl.substring(0, frontBaseUrl.length() - 1) : frontBaseUrl;
    }

    /** 링크를 만들어 돌려준다(메일 없음). 서버 명령이 쓴다. */
    @Transactional
    public String issueLink(Member member) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.deleteByMemberId(member.getId());
        tokens.save(new PasswordResetToken(member.getId(), hash(token), Instant.now().plus(TTL)));
        return frontBaseUrl + "/reset/?token=" + token;
    }

    @Transactional
    public void request(String rawEmail) {
        Member member = members.findByEmail(Member.normalizeEmail(rawEmail)).filter(Member::isActive).orElse(null);
        if (member == null) {
            log.info("비밀번호 찾기 — 해당 없음");
            return;
        }
        String link = issueLink(member);
        mailer.send(member.getEmail(), "[THE MANLY] 비밀번호 재설정 안내",
                member.getName() + "님,\n\n아래 링크에서 새 비밀번호를 정해 주세요. 30분 동안 한 번만 쓸 수 있습니다.\n\n"
                        + link + "\n\n요청하지 않으셨다면 이 메일을 무시해 주세요.\n\nTHE MANLY");
    }

    @Transactional
    public void reset(String token, String newPassword) {
        Instant now = Instant.now();
        PasswordResetToken found = tokens.findByTokenHash(hash(token)).filter(t -> t.isUsable(now))
                .orElseThrow(InvalidTokenException::new);
        Member member = members.findById(found.getMemberId()).filter(Member::isActive)
                .orElseThrow(InvalidTokenException::new);
        passwordPolicy.validate(newPassword, member.getEmail());
        member.changePassword(passwordEncoder.encode(newPassword));
        found.markUsed(now);
        sessionTerminator.terminateAll(member.getEmail());
        log.info("비밀번호 재설정 memberId={}", member.getId());
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 링크가 없거나 · 만료됐거나 · 이미 쓰였다. 셋을 구분하지 않는다. */
    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException() {
            super("invalid reset token");
        }
    }
}
