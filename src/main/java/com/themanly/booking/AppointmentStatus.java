package com.themanly.booking;

import java.util.EnumSet;
import java.util.Set;

/** 예약 상태. {@link #LIVE} 는 자리를 차지하고 있는 상태 — 같은 시각에 둘일 수 없다. */
public enum AppointmentStatus {
    /** 손님이 신청했고 관리자가 아직 확인하지 않았다 */
    REQUESTED("신청"),
    /** 관리자가 확정했다 */
    CONFIRMED("확정"),
    CANCELLED("취소"),
    COMPLETED("완료"),
    /** 확정했는데 오지 않았다 */
    NO_SHOW("불참");

    public static final Set<AppointmentStatus> LIVE = EnumSet.of(REQUESTED, CONFIRMED);

    private final String label;

    AppointmentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isLive() {
        return LIVE.contains(this);
    }
}
