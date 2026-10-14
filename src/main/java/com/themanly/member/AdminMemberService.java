package com.themanly.member;

import com.themanly.auth.SessionTerminator;
import com.themanly.common.error.DomainRuleException;
import com.themanly.common.error.ResourceNotFoundException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 관리 (관리자) — 검색 · 정지 · 잠금 해제 · 역할 · 서버 명령으로 관리자 만들기.
 * 역할을 바꾸면 그 사람의 세션을 끊는다 — 세션에 옛 권한이 들어 있다.
 */
@Service
public class AdminMemberService {

    private static final Logger log = LoggerFactory.getLogger(AdminMemberService.class);
    static final int PAGE_SIZE = 20;
    /** 숫자가 없는 검색어일 때 전화번호 조건이 아무것도 걸지 않게. */
    private static final String MATCH_NOTHING = "#";

    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final SessionTerminator sessionTerminator;

    public AdminMemberService(MemberRepository members, PasswordEncoder passwordEncoder, SessionTerminator sessionTerminator) {
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.sessionTerminator = sessionTerminator;
    }

    @Transactional(readOnly = true)
    public Page<Member> search(String query, MemberStatus status, int page) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String pattern = "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        String digits = q.replaceAll("[^0-9]", "");
        Set<MemberStatus> statuses = status == null ? EnumSet.allOf(MemberStatus.class) : EnumSet.of(status);
        return members.search(pattern, digits.isEmpty() ? MATCH_NOTHING : "%" + digits + "%", statuses,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Transactional(readOnly = true)
    public Member get(UUID memberId) {
        return members.findById(memberId).orElseThrow(() -> new ResourceNotFoundException("회원 없음 id=" + memberId));
    }

    @Transactional
    public void unlock(UUID memberId) {
        get(memberId).unlock();
        log.info("잠금 해제 memberId={}", memberId);
    }

    /** 정지. 자기 자신 · 관리자는 정지할 수 없다. 세션을 끊는다. */
    @Transactional
    public void suspend(UUID actorId, UUID memberId) {
        Member member = findOther(actorId, memberId);
        if (member.getRole().isAdmin()) {
            throw new DomainRuleException("MEMBER_RULE", "관리자 계정은 정지할 수 없습니다. 먼저 권한을 해제해 주세요.");
        }
        member.suspend();
        sessionTerminator.terminateAll(member.getEmail());
        log.info("이용 정지 memberId={} actorId={}", memberId, actorId);
    }

    @Transactional
    public void reactivate(UUID actorId, UUID memberId) {
        findOther(actorId, memberId).reactivate();
        log.info("정지 해제 memberId={} actorId={}", memberId, actorId);
    }

    /** 관리자 지정 · 해제. SUPER_ADMIN 만(SecurityConfig). SUPER_ADMIN 은 화면에서 만들 수도 내릴 수도 없다. */
    @Transactional
    public void changeRole(UUID actorId, UUID memberId, MemberRole newRole) {
        if (newRole == MemberRole.SUPER_ADMIN) {
            throw new DomainRuleException("MEMBER_RULE", "최고 관리자는 서버 명령으로만 만들 수 있습니다.");
        }
        Member member = findOther(actorId, memberId);
        if (member.getRole() == MemberRole.SUPER_ADMIN) {
            throw new DomainRuleException("MEMBER_RULE", "최고 관리자의 권한은 화면에서 바꿀 수 없습니다.");
        }
        if (!member.isActive()) {
            throw new DomainRuleException("MEMBER_RULE", "정지되었거나 탈퇴한 회원입니다.");
        }
        if (member.getRole() == newRole) {
            return;
        }
        member.changeRole(newRole);
        sessionTerminator.terminateAll(member.getEmail());
        log.info("역할 변경 memberId={} role={} actorId={}", memberId, newRole, actorId);
    }

    /** 서버 명령(create-admin). 없으면 만들고(비밀번호는 무작위 — 설정 링크로 본인이 정한다), 있으면 역할만 올린다. */
    @Transactional
    public CommandResult createOrPromoteByCommand(String rawEmail, String name, MemberRole role) {
        requireAdminRole(role);
        String email = Member.normalizeEmail(rawEmail);
        Member existing = members.findByEmail(email).orElse(null);
        if (existing != null) {
            if (!existing.isActive()) {
                throw new DomainRuleException("MEMBER_RULE", "정지되었거나 탈퇴한 계정입니다.");
            }
            existing.changeRole(role);
            sessionTerminator.terminateAll(existing.getEmail());
            return new CommandResult(existing, false);
        }
        Member created = new Member(UUID.randomUUID(), email, passwordEncoder.encode(UUID.randomUUID().toString()), name, null);
        created.changeRole(role);
        members.save(created);
        log.info("관리자 생성(명령) memberId={} role={}", created.getId(), role);
        return new CommandResult(created, true);
    }

    /**
     * 아이디 로그인 관리자(서버 명령). 임시 비밀번호가 있으면(로컬 전용) 그 값으로 들어오고 바꾸기 전까지 관리자 API 가 막힌다.
     * null 이면(운영) 무작위 비밀번호로 만들고 명령이 설정 링크를 찍는다. 이메일이 없으면 {아이디}@local.invalid.
     */
    @Transactional
    public CommandResult createWithLoginId(String rawLoginId, String rawEmail, String name, MemberRole role, String temporaryPassword) {
        requireAdminRole(role);
        String loginId = rawLoginId.trim().toLowerCase(Locale.ROOT);
        if (!loginId.matches("^[a-z0-9_]{4,30}$")) {
            throw new DomainRuleException("MEMBER_RULE", "아이디는 영문 소문자 · 숫자 · _ 로 4~30자여야 합니다.");
        }
        Member existing = members.findByLoginId(loginId).orElse(null);
        if (existing != null) {
            existing.changeRole(role);
            if (temporaryPassword != null) {
                existing.setTemporaryPassword(passwordEncoder.encode(temporaryPassword));
            }
            sessionTerminator.terminateAll(existing.getEmail());
            return new CommandResult(existing, false);
        }
        String email = rawEmail == null ? loginId + "@local.invalid" : Member.normalizeEmail(rawEmail);
        if (members.existsByEmail(email)) {
            throw new DomainRuleException("MEMBER_RULE", "그 이메일의 계정이 이미 있습니다. --email 을 빼거나 다른 주소를 쓰세요.");
        }
        Member created = new Member(UUID.randomUUID(), email, passwordEncoder.encode(UUID.randomUUID().toString()), name, null);
        created.assignLoginId(loginId);
        created.changeRole(role);
        if (temporaryPassword != null) {
            created.setTemporaryPassword(passwordEncoder.encode(temporaryPassword));
        }
        members.save(created);
        log.info("관리자 생성(명령 · 아이디) memberId={} role={}", created.getId(), role);
        return new CommandResult(created, true);
    }

    private static void requireAdminRole(MemberRole role) {
        if (!role.isAdmin()) {
            throw new DomainRuleException("MEMBER_RULE", "--role 은 ADMIN 또는 SUPER_ADMIN 이어야 합니다.");
        }
    }

    private Member findOther(UUID actorId, UUID memberId) {
        if (actorId.equals(memberId)) {
            throw new DomainRuleException("MEMBER_RULE", "자기 자신에게는 할 수 없는 작업입니다.");
        }
        return get(memberId);
    }

    public record CommandResult(Member member, boolean created) {
    }
}
