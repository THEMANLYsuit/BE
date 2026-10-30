package com.themanly.rental;
import java.util.EnumSet;
import java.util.Set;
public enum RentalStatus {
    REQUESTED("신청"),
    CONFIRMED("확정"),
    PICKED_UP("대여 중"),
    RETURNED("반납"),
    CANCELLED("취소");
    public static final Set<RentalStatus> LIVE = EnumSet.of(REQUESTED, CONFIRMED, PICKED_UP);
    private final String label;
    RentalStatus(String label){this.label = label;}
    public String label(){return label;}
    public boolean isLive(){return LIVE.contains(this);}
}
