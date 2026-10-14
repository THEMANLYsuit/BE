package com.themanly.booking;

/** 예약 목적. 자리 길이(분)는 목적마다 다르다 — 상담 · 가봉 60분, 수령 30분. */
public enum AppointmentType {
    CONSULTATION("첫 상담", 60),
    FITTING("가봉", 60),
    PICKUP("수령", 30),
    OTHER("기타", 60);

    private final String label;
    private final int durationMinutes;

    AppointmentType(String label, int durationMinutes) {
        this.label = label;
        this.durationMinutes = durationMinutes;
    }

    public String label() {
        return label;
    }

    public int durationMinutes() {
        return durationMinutes;
    }
}
