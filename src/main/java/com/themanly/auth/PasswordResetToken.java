package com.themanly.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 비밀번호 설정 · 재설정 링크의 토큰 (V2). 원문은 메일 속 링크에만 있고 DB 에는 해시만 둔다 — DB 가 새도 링크를 만들 수 없다.
 * 30분, 한 번만 쓴다.
 */
@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {

    @Id
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected PasswordResetToken() {
        // JPA
    }

    public PasswordResetToken(UUID memberId, String tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.memberId = memberId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public UUID getMemberId() {
        return memberId;
    }
}
