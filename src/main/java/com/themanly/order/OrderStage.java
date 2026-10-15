package com.themanly.order;

public enum OrderStage {
    CONSULTATION("상담"),
    MEASUREMENT("치수"),
    FABRIC("원단 · 스타일"),
    FITTING("가봉"),
    ADJUSTMENT("수정 · 완성"),
    DELIVERY("수령");

    private final String label;

    OrderStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}