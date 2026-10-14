package com.themanly.member;

import java.util.List;

/**
 * 회원 역할. 기본은 {@link #MEMBER}(손님).
 * <ul>
 *   <li>{@link #ADMIN} — 예약 · 치수 · 주문 · 카탈로그 운영. {@link #SUPER_ADMIN} 이 회원 관리에서 지정한다.</li>
 *   <li>{@link #SUPER_ADMIN} — 관리자 지정 · 해제. <b>서버 명령(create-admin)으로만</b> 만든다.</li>
 * </ul>
 */
public enum MemberRole {
    MEMBER,
    ADMIN,
    SUPER_ADMIN;

    /** SUPER_ADMIN 은 ADMIN 권한도 함께 갖는다 — {@code hasRole("ADMIN")} 규칙 하나로 둘 다 들어온다. */
    public List<String> authorities() {
        return switch (this) {
            case MEMBER -> List.of("ROLE_MEMBER");
            case ADMIN -> List.of("ROLE_ADMIN");
            case SUPER_ADMIN -> List.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN");
        };
    }

    public boolean isAdmin() {
        return this != MEMBER;
    }
}
