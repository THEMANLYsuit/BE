package com.themanly.rental.api;

import com.themanly.auth.MemberPrincipal;
import com.themanly.rental.Rental;
import com.themanly.rental.RentalItem;
import com.themanly.rental.RentalItemService;
import com.themanly.rental.RentalService;
import com.themanly.rental.RentalStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RentalController {

    private final RentalItemService itemService;
    private final RentalService rentals;

    public RentalController(RentalItemService itemService, RentalService rentals) {
        this.itemService = itemService;
        this.rentals = rentals;
    }

    @GetMapping("/api/rental-items")
    public List<ItemView> items() {
        return itemService.visibleItems().stream().map(ItemView::from).toList();
    }

    @GetMapping("/api/rental-items/{id}")
    public ItemView item(@PathVariable UUID id) {
        return ItemView.from(itemService.visibleItem(id));
    }

    @PostMapping("/api/rentals")
    public ResponseEntity<View> request(@AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody Request in) {
        Rental r = rentals.request(principal.getId(), in.itemId(), in.size(), in.pickupDate(), in.returnDate(), in.note());
        return ResponseEntity.status(HttpStatus.CREATED).body(View.from(r));
    }

    @GetMapping("/api/rentals/mine")
    public List<View> mine(@AuthenticationPrincipal MemberPrincipal principal) {
        return rentals.mine(principal.getId()).stream().map(View::from).toList();
    }

    @PostMapping("/api/rentals/{id}/cancel")
    public View cancel(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable UUID id) {
        return View.from(rentals.cancelByCustomer(principal.getId(), id));
    }

    public record Request(@NotNull UUID itemId, @Size(max = 20) String size, @NotNull LocalDate pickupDate,
                          @NotNull LocalDate returnDate, @Size(max = 1000) String note) {
    }

    public record ItemView(UUID id, String name, String category, String description, String imageUrl, Long priceKrw,
                           Long depositKrw, int rentalDays, List<String> sizes, boolean rentable) {
        static ItemView from(RentalItem i) {
            return new ItemView(i.getId(), i.getName(), i.getCategory(), i.getDescription(), i.getImageUrl(), i.getPriceKrw(),
                    i.getDepositKrw(), i.getRentalDays(), i.sizes(), i.isRentable());
        }
    }

    public record View(UUID id, UUID itemId, String itemName, Long priceKrw, String size, LocalDate pickupDate,
                       LocalDate returnDate, RentalStatus status, String statusLabel, String note, String cancelReason,
                       Instant createdAt) {
        static View from(Rental r) {
            return new View(r.getId(), r.getItemId(), r.getItemName(), r.getPriceKrw(), r.getSizeLabel(), r.getPickupDate(),
                    r.getReturnDate(), r.getStatus(), r.getStatus().label(), r.getCustomerNote(),
                    r.getCancelledBy() == Rental.CancelledBy.ADMIN ? r.getCancelReason() : null, r.getCreatedAt());
        }
    }
}