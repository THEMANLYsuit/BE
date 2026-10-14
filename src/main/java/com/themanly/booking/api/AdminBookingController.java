package com.themanly.booking.api;

import com.themanly.booking.Appointment;
import com.themanly.booking.AppointmentStatus;
import com.themanly.booking.AppointmentType;
import com.themanly.booking.BookingService;
import com.themanly.member.Member;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 예약 관리 (관리자). */
@RestController
@RequestMapping("/api/admin/bookings")
public class AdminBookingController {

    private final BookingService bookings;

    public AdminBookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @GetMapping
    public List<View> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(required = false) AppointmentStatus status) {
        return bookings.list(from, to, status).stream().map(View::from).toList();
    }

    @GetMapping("/{id}")
    public View get(@PathVariable UUID id) {
        return View.from(bookings.get(id));
    }

    @PostMapping("/{id}/confirm")
    public View confirm(@PathVariable UUID id) {
        return View.from(bookings.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    public View cancel(@PathVariable UUID id, @Valid @RequestBody Cancel in) {
        return View.from(bookings.cancelByAdmin(id, in.reason()));
    }

    @PostMapping("/{id}/complete")
    public View complete(@PathVariable UUID id) {
        return View.from(bookings.complete(id));
    }

    @PostMapping("/{id}/no-show")
    public View noShow(@PathVariable UUID id) {
        return View.from(bookings.markNoShow(id));
    }

    @PutMapping("/{id}/schedule")
    public View reschedule(@PathVariable UUID id, @Valid @RequestBody Schedule in) {
        return View.from(bookings.reschedule(id, in.startsAt()));
    }

    @PutMapping("/{id}/note")
    public View note(@PathVariable UUID id, @Valid @RequestBody Note in) {
        return View.from(bookings.note(id, in.adminNote()));
    }

    public record Cancel(@Size(max = 300) String reason) {
    }

    public record Schedule(@NotNull Instant startsAt) {
    }

    public record Note(@Size(max = 1000) String adminNote) {
    }

    public record Customer(UUID id, String name, String email, String phone) {
    }

    public record View(UUID id, AppointmentType type, String typeLabel, AppointmentStatus status, String statusLabel,
                       Instant startsAt, int durationMinutes, String customerNote, String adminNote,
                       Appointment.CancelledBy cancelledBy, String cancelReason, Instant confirmedAt, Instant cancelledAt,
                       Instant createdAt, Customer customer) {
        static View from(BookingService.WithMember w) {
            Appointment a = w.appointment();
            Member m = w.member();
            return new View(a.getId(), a.getType(), a.getType().label(), a.getStatus(), a.getStatus().label(), a.getStartsAt(),
                    a.getDurationMinutes(), a.getCustomerNote(), a.getAdminNote(), a.getCancelledBy(), a.getCancelReason(),
                    a.getConfirmedAt(), a.getCancelledAt(), a.getCreatedAt(),
                    m == null ? null : new Customer(m.getId(), m.getName(), m.getEmail(), m.getPhone()));
        }
    }
}
