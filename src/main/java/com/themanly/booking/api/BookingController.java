package com.themanly.booking.api;

import com.themanly.auth.MemberPrincipal;
import com.themanly.booking.Appointment;
import com.themanly.booking.AppointmentStatus;
import com.themanly.booking.AppointmentType;
import com.themanly.booking.BookingService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 손님 예약. 빈 시간은 로그인 없이, 신청 · 내 예약 · 취소는 로그인해야 한다(SecurityConfig). */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookings;
    private final BespokeOrderRepository orders;

    public BookingController(BookingService bookings, BespokeOrderRepository orders) {
        this.bookings = bookings;
        this.orders = orders;
    }

    /** 빈 시간. {@code from} · {@code to} 는 yyyy-MM-dd(한국 날짜). 31일 이내. */
    @GetMapping("/availability")
    public List<BookingService.DayAvailability> availability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return bookings.availability(from, to);
    }

    @PostMapping
    public ResponseEntity<View> request(@AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody Request in) {
        Appointment a = bookings.request(principal.getId(), in.type(), in.startsAt(), in.note(), in.orderNumber());
        return ResponseEntity.status(HttpStatus.CREATED).body(view(a));
    }

    @GetMapping("/mine")
    public List<View> mine(@AuthenticationPrincipal MemberPrincipal principal) {
        return bookings.mine(principal.getId()).stream().map(this::view).toList();
    }

    @PostMapping("/{id}/cancel")
    public View cancel(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable UUID id) {
        return view(bookings.cancelByCustomer(principal.getId(), id));
    }

    private View view(Appointment a) {
        String orderNumber = a.getOrderId() == null ? null
                : orders.findById(a.getOrderId()).map(o -> o.getOrderNumber()).orElse(null);
        return View.from(a, orderNumber);
    }

    /** orderNumber — 가봉 · 수령 예약이면 어느 주문인지(내 진행 중 주문). 상담이면 비운다. */
    public record Request(@NotNull AppointmentType type, @NotNull Instant startsAt, @Size(max = 1000) String note,
                          @Size(max = 20) String orderNumber) {
    }

    /** 손님에게 보이는 예약. 관리자 메모는 나가지 않는다. */
    public record View(UUID id, AppointmentType type, String typeLabel, AppointmentStatus status, String statusLabel,
                       Instant startsAt, int durationMinutes, String note, String cancelReason, Instant createdAt,
                       String orderNumber) {
        static View from(Appointment a, String orderNumber) {
            return new View(a.getId(), a.getType(), a.getType().label(), a.getStatus(), a.getStatus().label(), a.getStartsAt(),
                    a.getDurationMinutes(), a.getCustomerNote(),
                    a.getCancelledBy() == Appointment.CancelledBy.ADMIN ? a.getCancelReason() : null, a.getCreatedAt(),
                    orderNumber);
        }
    }
}
