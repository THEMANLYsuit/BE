package com.themanly.member.api;

import com.themanly.auth.MemberPrincipal;
import com.themanly.member.AdminMemberService;
import com.themanly.member.Member;
import com.themanly.member.MemberRole;
import com.themanly.member.MemberStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 회원 관리 (관리자). {@code /api/admin/**} 규칙으로 ADMIN 만, 역할 변경은 SUPER_ADMIN 만(SecurityConfig). */
@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

    private final AdminMemberService members;

    public AdminMemberController(AdminMemberService members) {
        this.members = members;
    }

    @GetMapping
    public PageView list(@RequestParam(required = false) String q, @RequestParam(required = false) MemberStatus status,
                         @RequestParam(defaultValue = "0") int page) {
        Page<Member> found = members.search(q, status, page);
        return new PageView(found.getContent().stream().map(View::from).toList(), found.getNumber(), found.getTotalPages(),
                found.getTotalElements());
    }

    @GetMapping("/{id}")
    public View detail(@PathVariable UUID id) {
        return View.from(members.get(id));
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<Void> unlock(@PathVariable UUID id) {
        members.unlock(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<Void> suspend(@AuthenticationPrincipal MemberPrincipal actor, @PathVariable UUID id) {
        members.suspend(actor.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<Void> reactivate(@AuthenticationPrincipal MemberPrincipal actor, @PathVariable UUID id) {
        members.reactivate(actor.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<Void> changeRole(@AuthenticationPrincipal MemberPrincipal actor, @PathVariable UUID id,
                                           @Valid @RequestBody RoleChange request) {
        members.changeRole(actor.getId(), id, request.role());
        return ResponseEntity.noContent().build();
    }

    public record RoleChange(@NotNull MemberRole role) {
    }

    public record View(UUID id, String email, String name, String phone, MemberStatus status, MemberRole role, String loginId,
                       boolean locked, Instant lastLoginAt, Instant createdAt) {
        public static View from(Member m) {
            return new View(m.getId(), m.getEmail(), m.getName(), m.getPhone(), m.getStatus(), m.getRole(), m.getLoginId(),
                    m.isLocked(Instant.now()), m.getLastLoginAt(), m.getCreatedAt());
        }
    }

    public record PageView(List<View> items, int page, int totalPages, long total) {
    }
}
