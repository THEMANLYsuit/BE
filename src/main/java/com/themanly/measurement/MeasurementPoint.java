package com.themanly.measurement;

import java.math.BigDecimal;

public enum MeasurementPoint {
    HEIGHT(Group.BODY, "키", Unit.CM, 120, 220),
    WEIGHT(Group.BODY, "몸무게", Unit.KG, 30, 200),

    NECK(Group.TOP, "목둘레", Unit.CM, 25, 60),
    CHEST(Group.TOP, "가슴둘레", Unit.CM, 60, 180),
    WAIST(Group.TOP, "허리둘레", Unit.CM, 50, 180),
    BELLY(Group.TOP, "배둘레", Unit.CM, 50, 190),
    SHOULDER(Group.TOP, "어깨너비", Unit.CM, 30, 65),
    BACK_WIDTH(Group.TOP, "등너비", Unit.CM, 25, 60),
    FRONT_LENGTH(Group.TOP, "앞길이", Unit.CM, 30, 70),
    BACK_LENGTH(Group.TOP, "등길이", Unit.CM, 30, 70),
    JACKET_LENGTH(Group.TOP, "재킷 기장", Unit.CM, 55, 95),
    SLEEVE_LEFT(Group.TOP, "소매 기장 (왼)", Unit.CM, 45, 75),
    SLEEVE_RIGHT(Group.TOP, "소매 기장 (오른)", Unit.CM, 45, 75),
    BICEP(Group.TOP, "팔뚝둘레", Unit.CM, 20, 60),
    WRIST(Group.TOP, "손목둘레", Unit.CM, 12, 30),

    HIP(Group.BOTTOM, "엉덩이둘레", Unit.CM, 60, 180),
    TROUSER_WAIST(Group.BOTTOM, "바지 허리", Unit.CM, 55, 160),
    RISE(Group.BOTTOM, "밑위", Unit.CM, 18, 45),
    THIGH(Group.BOTTOM, "허벅지둘레", Unit.CM, 35, 90),
    KNEE(Group.BOTTOM, "무릎둘레", Unit.CM, 30, 60),
    CALF(Group.BOTTOM, "종아리둘레", Unit.CM, 25, 60),
    HEM(Group.BOTTOM, "밑단둘레", Unit.CM, 30, 60),
    OUTSEAM(Group.BOTTOM, "바지 기장", Unit.CM, 80, 130),
    INSEAM(Group.BOTTOM, "안쪽 기장", Unit.CM, 60, 100);

    public enum Group {
        BODY("체형"), TOP("상의"), BOTTOM("하의");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Unit {
        CM("cm"), KG("kg");

        private final String symbol;

        Unit(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }

    private final Group group;
    private final String label;
    private final Unit unit;
    private final int min;
    private final int max;

    MeasurementPoint(Group group, String label, Unit unit, int min, int max) {
        this.group = group;
        this.label = label;
        this.unit = unit;
        this.min = min;
        this.max = max;
    }

    public boolean accepts(BigDecimal value) {
        return value.compareTo(BigDecimal.valueOf(min)) >= 0 && value.compareTo(BigDecimal.valueOf(max)) <= 0;
    }

    public Group group() {
        return group;
    }

    public String label() {
        return label;
    }

    public Unit unit() {
        return unit;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }
}