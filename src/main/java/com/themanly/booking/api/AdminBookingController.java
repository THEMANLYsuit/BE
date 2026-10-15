package com.themanly.booking.api;

import com.themanly.booking.Appointment;
import com.themanly.booking.AppointmentStatus;
import com.themanly.booking.AppointmentType;
import com.themanly.booking.BookingService;
import com.themanly.member.Member;
import com.themanly.order.BespokeOrderRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/admin")
public class AdminBookingController {

    private final BookingService bookings;
    private final BespokeOrderRepository orders;

    public AdminBookingController(BookingService bookings, BespokeOrderRepository orders) {
        this.bookings = bookings;
        this.orders = orders;
    }

    /** 관리자가 상담 중에 잡는 예약 — 바로 확정. orderNumber 로 그 손님의 진행 중 주문에 묶는다. */
    @PostMapping("/members/{memberId}/bookings")
    public ResponseEntity<View> create(@PathVariable UUID memberId, @Valid @RequestBody Create in) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(view(bookings.createByAdmin(memberId, in.type(), in.startsAt(), in.adminNote(), in.orderNumber())));
    }

    @GetMapping("/bookings")
    public List<View> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(required = false) AppointmentStatus status) {
        return bookings.list(from, to, status).stream().map(this::view).toList();
    }

    @GetMapping("/bookings/{id}")
    public View get(@PathVariable UUID id) {
        return view(bookings.get(id));
    }

    @PostMapping("/bookings/{id}/confirm")
    public View confirm(@PathVariable UUID id) {
        return view(bookings.confirm(id));
    }

    @PostMapping("/bookings/{id}/cancel")
    public View cancel(@PathVariable UUID id, @Valid @RequestBody Cancel in) {
        return view(bookings.cancelByAdmin(id, in.reason()));
    }

    @PostMapping("/bookings/{id}/complete")
    public View complete(@PathVariable UUID id) {
        return view(bookings.complete(id));
    }

    @PostMapping("/bookings/{id}/no-show")
    public View noShow(@PathVariable UUID id) {
        return view(bookings.markNoShow(id));
    }

    @PutMapping("/bookings/{id}/schedule")
    public View reschedule(@PathVariable UUID id, @Valid @RequestBody Schedule in) {
        return view(bookings.reschedule(id, in.startsAt()));
    }

    @PutMapping("/bookings/{id}/note")
    public View note(@PathVariable UUID id, @Valid @RequestBody Note in) {
        return view(bookings.note(id, in.adminNote()));
    }

    private View view(BookingService.WithMember w) {
        String orderNumber = w.appointment().getOrderId() == null ? null
                : orders.findById(w.appointment().getOrderId()).map(o -> o.getOrderNumber()).orElse(null);
        return View.from(w, orderNumber);
    }

    public record Create(@NotNull AppointmentType type, @NotNull Instant startsAt, @Size(max = 1000) String adminNote,
                         @Size(max = 20) String orderNumber) {
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
                       Instant createdAt, Customer customer, String orderNumber) {
        static View from(BookingService.WithMember w, String orderNumber) {
            Appointment a = w.appointment();
            Member m = w.member();
            return new View(a.getId(), a.getType(), a.getType().label(), a.getStatus(), a.getStatus().label(), a.getStartsAt(),
                    a.getDurationMinutes(), a.getCustomerNote(), a.getAdminNote(), a.getCancelledBy(), a.getCancelReason(),
                    a.getConfirmedAt(), a.getCancelledAt(), a.getCreatedAt(),
                    m == null ? null : new Customer(m.getId(), m.getName(), m.getEmail(), m.getPhone()), orderNumber);
        }
    }
}
