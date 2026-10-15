package com.themanly.order;

public enum OrderStatus {
    IN_PROGRESS("진행 중"),
    COMPLETED("수령 완료"),
    CANCELLED("취소");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}