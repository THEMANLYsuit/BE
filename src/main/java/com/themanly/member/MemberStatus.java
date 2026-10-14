package com.themanly.member;

/**
 * 회원 상태. 잠금(로그인 실패 누적)은 여기 없다 — 시간이 지나면 저절로 풀리는 조건이라 {@code locked_until} 시각으로 둔다.
 */
public enum MemberStatus {
    ACTIVE,
    /** 이용 정지. 관리자가 걸고 관리자가 푼다. 로그인할 수 없다. */
    SUSPENDED,
    /** 탈퇴. 행은 남기고(예약 · 주문 이력이 참조한다) 개인정보를 지운다. 로그인에서는 없는 계정과 같다. */
    WITHDRAWN
}
