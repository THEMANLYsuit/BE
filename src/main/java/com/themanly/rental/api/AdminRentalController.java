package com.themanly.rental.api;

import com.themanly.member.Member;
import com.themanly.rental.Rental;
import com.themanly.rental.RentalItem;
import com.themanly.rental.RentalItemService;
import com.themanly.rental.RentalService;
import com.themanly.rental.RentalStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
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

/** 렌탈 관리 (관리자) — 대여 옷과 대여 신청. 길이 제한은 V15 의 CHECK 와 같다. */
@RestController
@RequestMapping("/api/admin")
public class AdminRentalController {

    private final RentalItemService itemService;
    private final RentalService rentals;

    public AdminRentalController(RentalItemService itemService, RentalService rentals) {
        this.itemService = itemService;
        this.rentals = rentals;
    }

    // ── 대여 옷 ──────────────────────────────────────────

    @GetMapping("/rental-items")
    public List<ItemView> items() {
        return itemService.allItems().stream().map(ItemView::from).toList();
    }

    @GetMapping("/rental-items/{id}")
    public ItemView item(@PathVariable UUID id) {
        return ItemView.from(itemService.item(id));
    }

    @PostMapping("/rental-items")
    public ResponseEntity<ItemView> createItem(@Valid @RequestBody ItemInput in) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ItemView.from(itemService.create(in.details())));
    }

    @PutMapping("/rental-items/{id}")
    public ItemView updateItem(@PathVariable UUID id, @Valid @RequestBody ItemInput in) {
        return ItemView.from(itemService.update(id, in.details()));
    }

    @PutMapping("/rental-items/{id}/status")
    public ItemView changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusInput in) {
        return ItemView.from(itemService.changeStatus(id, in.status()));
    }

    // ── 대여 신청 ────────────────────────────────────────

    /** 수령일이 {@code from} ~ {@code to} 안인 대여. 3개월 이내. */
    @GetMapping("/rentals")
    public List<View> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(required = false) RentalStatus status) {
        return rentals.list(from, to, status).stream().map(View::from).toList();
    }

    @GetMapping("/rentals/{id}")
    public View get(@PathVariable UUID id) {
        return View.from(rentals.get(id));
    }

    @PostMapping("/rentals/{id}/confirm")
    public View confirm(@PathVariable UUID id) {
        return View.from(rentals.confirm(id));
    }

    @PostMapping("/rentals/{id}/pick-up")
    public View pickUp(@PathVariable UUID id) {
        return View.from(rentals.pickUp(id));
    }

    @PostMapping("/rentals/{id}/return")
    public View markReturned(@PathVariable UUID id) {
        return View.from(rentals.markReturned(id));
    }

    @PostMapping("/rentals/{id}/cancel")
    public View cancel(@PathVariable UUID id, @Valid @RequestBody Cancel in) {
        return View.from(rentals.cancelByAdmin(id, in.reason()));
    }

    @PutMapping("/rentals/{id}/schedule")
    public View reschedule(@PathVariable UUID id, @Valid @RequestBody Schedule in) {
        return View.from(rentals.reschedule(id, in.pickupDate(), in.returnDate()));
    }

    @PutMapping("/rentals/{id}/note")
    public View note(@PathVariable UUID id, @Valid @RequestBody Note in) {
        return View.from(rentals.note(id, in.adminNote()));
    }

    // ── 입력 ────────────────────────────────────────────

    public record ItemInput(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 40) String category,
            @Size(max = 2000) String description,
            @Size(max = 500) @Pattern(regexp = "^$|^(https://|/)\\S+$", message = "사진 주소는 https:// 또는 / 로 시작") String imageUrl,
            @PositiveOrZero Long priceKrw,
            @PositiveOrZero Long depositKrw,
            @Min(1) @Max(30) int rentalDays,
            @Size(max = 20) List<@NotBlank @Size(max = 20) @Pattern(regexp = "^[^,]+$", message = "사이즈에 쉼표를 넣을 수 없습니다") String> sizes) {

        RentalItem.Details details() {
            return new RentalItem.Details(name, category, description, imageUrl, priceKrw, depositKrw, rentalDays, sizes);
        }
    }

    public record StatusInput(@NotNull RentalItem.Status status) {
    }

    public record Cancel(@Size(max = 300) String reason) {
    }

    public record Schedule(@NotNull LocalDate pickupDate, @NotNull LocalDate returnDate) {
    }

    public record Note(@Size(max = 1000) String adminNote) {
    }

    // ── 응답 ────────────────────────────────────────────

    public record ItemView(UUID id, String name, String category, String description, String imageUrl, Long priceKrw,
                           Long depositKrw, int rentalDays, List<String> sizes, RentalItem.Status status, int sortOrder,
                           Instant updatedAt) {
        static ItemView from(RentalItem i) {
            return new ItemView(i.getId(), i.getName(), i.getCategory(), i.getDescription(), i.getImageUrl(), i.getPriceKrw(),
                    i.getDepositKrw(), i.getRentalDays(), i.sizes(), i.getStatus(), i.getSortOrder(), i.getUpdatedAt());
        }
    }

    public record Customer(UUID id, String name, String email, String phone) {
    }

    public record View(UUID id, UUID itemId, String itemName, Long priceKrw, String size, LocalDate pickupDate,
                       LocalDate returnDate, RentalStatus status, String statusLabel, String customerNote, String adminNote,
                       Rental.CancelledBy cancelledBy, String cancelReason, Instant confirmedAt, Instant pickedUpAt,
                       Instant returnedAt, Instant cancelledAt, Instant createdAt, Customer customer) {
        static View from(RentalService.WithMember w) {
            Rental r = w.rental();
            Member m = w.member();
            return new View(r.getId(), r.getItemId(), r.getItemName(), r.getPriceKrw(), r.getSizeLabel(), r.getPickupDate(),
                    r.getReturnDate(), r.getStatus(), r.getStatus().label(), r.getCustomerNote(), r.getAdminNote(),
                    r.getCancelledBy(), r.getCancelReason(), r.getConfirmedAt(), r.getPickedUpAt(), r.getReturnedAt(),
                    r.getCancelledAt(), r.getCreatedAt(),
                    m == null ? null : new Customer(m.getId(), m.getName(), m.getEmail(), m.getPhone()));
        }
    }
}