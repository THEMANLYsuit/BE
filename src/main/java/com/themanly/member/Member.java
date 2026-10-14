package com.themanly.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 회원 (V2).
 *
 * <p><b>평문 비밀번호가 이 클래스에 머무르지 않는다.</b> 생성자도 해시를 받는다. 해싱은 {@code AuthService} 가 경계에서 한 번만 한다.
 * 이메일은 항상 소문자다 — {@link #normalizeEmail} 로만 값을 만든다.
 */
@Entity
@Table(name = "member")
public class Member {

    /** 이 횟수만큼 연속으로 틀리면 잠근다. */
    public static final int MAX_FAILED_ATTEMPTS = 5;
    /** 잠금 시간(분). */
    public static final int LOCK_MINUTES = 15;
    /** 현재 이용약관 판. 약관을 개정하면 바꾼다 — 그 뒤 가입자는 새 판에 동의한 것으로 남는다. */
    public static final String CURRENT_TERMS_VERSION = "draft-2026-10-14";

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status = MemberStatus.ACTIVE;

    /** 바꾸는 길은 {@link #changeRole} 하나이고 이 패키지 안에서만 부른다 — 그곳이 곧 권한 상승 경로다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role = MemberRole.MEMBER;

    /** 관리자 아이디 로그인용. 서버 명령으로만 붙는다. */
    @Column(name = "login_id")
    private String loginId;

    /** 임시 비밀번호 계정. 바꾸기 전에는 관리자 API 가 막힌다 ({@code AdminPasswordGate}). */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "failed_login_attempts", nullable = false)
    private short failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "terms_agreed_at")
    private Instant termsAgreedAt;

    @Column(name = "terms_version")
    private String termsVersion;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Member() {
        // JPA
    }

    public Member(UUID id, String email, String passwordHash, String name, String phone) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = normalizeEmail(email);
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.name = Objects.requireNonNull(name, "name");
        this.phone = phone;
    }

    /** 이메일 정규화. 저장 · 조회 양쪽에서 반드시 거친다. {@link Locale#ROOT} — 서버 로캘에 따라 값이 달라지지 않게. */
    public static String normalizeEmail(String raw) {
        return Objects.requireNonNull(raw, "email").trim().toLowerCase(Locale.ROOT);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public boolean isSuspended() {
        return status == MemberStatus.SUSPENDED;
    }

    public boolean isWithdrawn() {
        return status == MemberStatus.WITHDRAWN;
    }

    /** 로그인 실패 한 번. 임계치를 넘으면 잠근다. 잠근 뒤 카운터를 0 으로 되돌리지 않는다 — 성공했을 때만 0 이 된다. */
    public void recordFailedLogin(Instant now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plusSeconds(LOCK_MINUTES * 60L);
        }
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    /** 비밀번호를 바꾼다. 임시 비밀번호 표시도 지운다 — 본인이 정한 비밀번호가 됐다. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "passwordHash");
        this.mustChangePassword = false;
    }

    /** 임시 비밀번호. 첫 로그인 뒤 바꾸기 전까지 관리자 API 가 막힌다. 서버 명령(로컬)만 쓴다. */
    void setTemporaryPassword(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.mustChangePassword = true;
    }

    void assignLoginId(String loginId) {
        this.loginId = loginId;
    }

    /** 약관 동의 · 만 14세 확인을 기록한다. 동의를 받았는지 판단하는 건 부르는 쪽이다. */
    public void agreeTerms(Instant now) {
        this.termsAgreedAt = Objects.requireNonNull(now, "now");
        this.termsVersion = CURRENT_TERMS_VERSION;
    }

    /**
     * 탈퇴 = 익명화. 행은 남기고 사람을 알아볼 수 있는 값을 전부 지운다. 이메일은 NOT NULL · UNIQUE 라 자리표시 주소를 넣는다 —
     * {@code .invalid} 는 절대 존재할 수 없는 도메인(RFC 2606). 세션은 이메일로 찾으므로 부르는 쪽이 익명화 <b>전에</b> 끊는다.
     */
    public void withdraw(Instant now) {
        this.status = MemberStatus.WITHDRAWN;
        this.withdrawnAt = Objects.requireNonNull(now, "now");
        this.email = "withdrawn-" + id + "@withdrawn.invalid";
        this.name = "탈퇴 회원";
        this.phone = null;
        this.loginId = null;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void suspend() {
        if (status != MemberStatus.ACTIVE) {
            throw new IllegalStateException("정지할 수 없는 상태 status=" + status);
        }
        this.status = MemberStatus.SUSPENDED;
    }

    public void reactivate() {
        if (status != MemberStatus.SUSPENDED) {
            throw new IllegalStateException("정지 상태가 아님 status=" + status);
        }
        this.status = MemberStatus.ACTIVE;
    }

    /** 로그인 실패 잠금을 사람이 푼다. */
    public void unlock() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    void changeRole(MemberRole newRole) {
        this.role = Objects.requireNonNull(newRole, "role");
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public MemberRole getRole() {
        return role;
    }

    public String getLoginId() {
        return loginId;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getTermsAgreedAt() {
        return termsAgreedAt;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
