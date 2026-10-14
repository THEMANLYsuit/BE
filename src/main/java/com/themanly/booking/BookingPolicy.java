package com.themanly.booking;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 예약 규칙 — 어느 날 어느 시각이 자리인가 (application.yml app.booking.*).
 *
 * <p>자리는 한국 시간 기준으로 하루를 {@code slotMinutes} 단위로 자른 것이다(영업 시작 ~ 종료 전). 휴무 요일은 자리가 없다.
 * 손님은 지금부터 {@code minLeadHours} 뒤부터 {@code maxAheadDays} 앞까지만 잡는다. 관리자는 시간을 옮길 때 이 앞뒤 제한을 받지 않는다.
 */
@Component
public class BookingPolicy {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final int openHour;
    private final int closeHour;
    private final int slotMinutes;
    private final Set<DayOfWeek> closedDays;
    private final Duration minLead;
    private final Duration maxAhead;

    public BookingPolicy(@Value("${app.booking.open-hour}") int openHour,
                         @Value("${app.booking.close-hour}") int closeHour,
                         @Value("${app.booking.slot-minutes}") int slotMinutes,
                         @Value("${app.booking.closed-days:}") List<String> closedDays,
                         @Value("${app.booking.min-lead-hours}") int minLeadHours,
                         @Value("${app.booking.max-ahead-days}") int maxAheadDays) {
        if (openHour < 0 || closeHour > 24 || openHour >= closeHour || slotMinutes < 15 || slotMinutes > 240) {
            throw new IllegalArgumentException("예약 규칙이 이상하다 open=" + openHour + " close=" + closeHour + " slot=" + slotMinutes);
        }
        this.openHour = openHour;
        this.closeHour = closeHour;
        this.slotMinutes = slotMinutes;
        Set<DayOfWeek> closed = EnumSet.noneOf(DayOfWeek.class);
        for (String d : closedDays) {
            if (!d.isBlank()) {
                closed.add(DayOfWeek.valueOf(d.trim().toUpperCase(Locale.ROOT)));
            }
        }
        this.closedDays = closed;
        this.minLead = Duration.ofHours(minLeadHours);
        this.maxAhead = Duration.ofDays(maxAheadDays);
    }

    public boolean isClosed(LocalDate date) {
        return closedDays.contains(date.getDayOfWeek());
    }

    /** 그날의 자리 시작 시각 전부(한국 시간). 휴무면 빈 목록. 영업 종료 전에 끝나는 자리만. */
    public List<Instant> slotsOn(LocalDate date) {
        if (isClosed(date)) {
            return List.of();
        }
        List<Instant> slots = new ArrayList<>();
        ZonedDateTime cursor = date.atTime(LocalTime.of(openHour, 0)).atZone(SEOUL);
        ZonedDateTime close = date.atTime(closeHour == 24 ? LocalTime.MAX : LocalTime.of(closeHour, 0)).atZone(SEOUL);
        while (!cursor.plusMinutes(slotMinutes).isAfter(close)) {
            slots.add(cursor.toInstant());
            cursor = cursor.plusMinutes(slotMinutes);
        }
        return slots;
    }

    /** 자리 눈금에 맞는 시각인가(영업 시간 안 · 단위에 맞음 · 휴무 아님). 앞뒤 제한은 보지 않는다. */
    public boolean isSlot(Instant startsAt) {
        LocalDate date = startsAt.atZone(SEOUL).toLocalDate();
        return slotsOn(date).contains(startsAt);
    }

    /**
     * 손님이 지금 잡을 수 있는 시각인가. 아니면 이유(손님에게 보이는 문장), 맞으면 {@code null}.
     */
    public String whyNotBookable(Instant startsAt, Instant now) {
        if (!isSlot(startsAt)) {
            return "예약할 수 없는 시간입니다. 영업 시간 안의 빈 시간을 골라 주세요.";
        }
        if (startsAt.isBefore(now.plus(minLead))) {
            return "예약은 최소 " + minLead.toHours() + "시간 뒤부터 가능합니다. 급하시면 매장으로 전화해 주세요.";
        }
        if (startsAt.isAfter(now.plus(maxAhead))) {
            return "예약은 " + maxAhead.toDays() + "일 앞까지만 받습니다.";
        }
        return null;
    }

    public Duration minLead() {
        return minLead;
    }

    public int slotMinutes() {
        return slotMinutes;
    }
}
