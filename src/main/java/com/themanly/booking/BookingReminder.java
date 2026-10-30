package com.themanly.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BookingReminder {

    private static final Logger log = LoggerFactory.getLogger(BookingReminder.class);
    private final AppointmentRepository appointments;
    private final ApplicationEventPublisher events;

    public BookingReminder(AppointmentRepository appointments, ApplicationEventPublisher events) {
        this.appointments = appointments;
        this.events = events;
    }
    @Scheduled(cron = "${app.booking.reminder-cron:0 0 18 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void run() {remindFor(LocalDate.now(BookingPolicy.SEOUL).plusDays(1));}

    @Transactional
    public int remindFor(LocalDate day) {
        Instant from = day.atStartOfDay(BookingPolicy.SEOUL).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(BookingPolicy.SEOUL).toInstant();
        List<Appointment> targets = appointments.findByStartsAtGreaterThanEqualAndStartsAtLessThanAndStatusAndRemindedAtIsNullOrderByStartsAtAsc(from, to, AppointmentStatus.CONFIRMED);
        Instant now = Instant.now();
        for (Appointment a : targets) {
            a.markReminded(now);
            events.publishEvent(new BookingNotifier.Event(BookingNotifier.Kind.REMINDER, a.getId()));
        }
        if (!targets.isEmpty()) {log.info("예약 전날 알림 day={} count={}", day, targets.size());}
        return targets.size();
    }
}