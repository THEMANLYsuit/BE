package com.themanly.order;

public enum Garment {
    SUIT_2PC("투피스 수트"),
    SUIT_3PC("쓰리피스 수트"),
    JACKET("재킷"),
    TROUSERS("바지"),
    VEST("베스트"),
    COAT("코트"),
    SHIRT("셔츠"),
    OTHER("기타");

    private final String label;

    Garment(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}