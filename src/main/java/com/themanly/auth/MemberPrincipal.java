package com.themanly.auth;

import com.themanly.member.Member;
import com.themanly.member.MemberRole;
import java.io.Serializable;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 세션에 담기는 로그인 주체. 직렬화되어 세션 테이블(SPRING_SESSION_ATTRIBUTES)에 저장된다.
 *
 * <p><b>비밀번호 해시를 담지 않는다.</b> 세션 테이블은 회원 테이블만큼 조심스럽게 다뤄지지 않는다.
 * 필드를 더할 때는 기존 세션이 역직렬화되지 않을 수 있다는 걸 염두에 둔다 — 꼭 필요한 것만 둔다.
 */
public class MemberPrincipal implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String email;
    private final String name;
    private final MemberRole role;

    public MemberPrincipal(UUID id, String email, String name, MemberRole role) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.role = role;
    }

    public static MemberPrincipal from(Member member) {
        return new MemberPrincipal(member.getId(), member.getEmail(), member.getName(), member.getRole());
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public MemberRole getRole() {
        return role;
    }

    /** 세션에 저장된 값이다 — DB 에서 역할을 바꿔도 이미 로그인한 세션에는 반영되지 않는다. 내릴 때는 세션도 끊는다. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return role.authorities().stream().map(SimpleGrantedAuthority::new).toList();
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }
}
