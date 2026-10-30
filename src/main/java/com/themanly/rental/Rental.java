package com.themanly.rental;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "rental")
public class Rental {

    public enum CancelledBy { CUSTOMER, ADMIN }
    @Id
    private UUID id;
    @Column(name = "member_id", nullable = false)
    private UUID memberId;
    @Column(name = "item_id", nullable = false)
    private UUID itemId;
    @Column(name = "item_name", nullable = false)
    private String itemName;
    @Column(name = "price_krw")
    private Long priceKrw;
    @Column(name = "size_label", nullable = false)
    private String sizeLabel;
    @Column(name = "pickup_date", nullable = false)
    private LocalDate pickupDate;
    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RentalStatus status = RentalStatus.REQUESTED;
    @Column(name = "customer_note", nullable = false)
    private String customerNote;
    @Column(name = "admin_note", nullable = false)
    private String adminNote;
    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by")
    private CancelledBy cancelledBy;
    @Column(name = "cancel_reason")
    private String cancelReason;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    @Column(name = "picked_up_at")
    private Instant pickedUpAt;
    @Column(name = "returned_at")
    private Instant returnedAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
    protected Rental() {}
    public static Rental request(UUID memberId, RentalItem item, String size, LocalDate pickupDate, LocalDate returnDate, String customerNote) {
        Rental r = new Rental();
        r.id = UUID.randomUUID();
        r.memberId = Objects.requireNonNull(memberId, "memberId");
        r.itemId = item.getId();
        r.itemName = item.getName();
        r.priceKrw = item.getPriceKrw();
        r.sizeLabel = size == null ? "" : size.trim();
        r.pickupDate = Objects.requireNonNull(pickupDate, "pickupDate");
        r.returnDate = Objects.requireNonNull(returnDate, "returnDate");
        r.customerNote = customerNote == null ? "" : customerNote.trim();
        r.adminNote = "";
        return r;
    }
    public boolean isOwnedBy(UUID memberId) {return this.memberId.equals(memberId);}
    public boolean isLive() {return status.isLive();}
    public boolean isBeforePickup() {return status == RentalStatus.REQUESTED || status == RentalStatus.CONFIRMED;}
    public void confirm(Instant now) {
        if (status != RentalStatus.REQUESTED) {throw new IllegalStateException("확정할 수 없는 상태 status=" + status);}
        this.status = RentalStatus.CONFIRMED;
        this.confirmedAt = now;
    }
    public void pickUp(Instant now) {
        if (status != RentalStatus.CONFIRMED) {throw new IllegalStateException("건넬 수 없는 상태 status=" + status);}
        this.status = RentalStatus.PICKED_UP;
        this.pickedUpAt = now;
    }
    public void markReturned(Instant now) {
        if (status != RentalStatus.PICKED_UP) {throw new IllegalStateException("반납 처리할 수 없는 상태 status=" + status);}
        this.status = RentalStatus.RETURNED;
        this.returnedAt = now;
    }
    public void cancel(CancelledBy by, String reason, Instant now) {
        if (!isBeforePickup()) {throw new IllegalStateException("취소할 수 없는 상태 status=" + status);}
        this.status = RentalStatus.CANCELLED;
        this.cancelledBy = Objects.requireNonNull(by, "by");
        this.cancelReason = reason == null || reason.isBlank() ? null : reason.trim();
        this.cancelledAt = now;
    }
    public void reschedule(LocalDate pickupDate, LocalDate returnDate, Instant now) {
        if (!isBeforePickup()) {
            throw new IllegalStateException("옮길 수 없는 상태 status=" + status);
        }
        this.pickupDate = Objects.requireNonNull(pickupDate, "pickupDate");
        this.returnDate = Objects.requireNonNull(returnDate, "returnDate");
        if (status == RentalStatus.REQUESTED) {
            this.status = RentalStatus.CONFIRMED;
            this.confirmedAt = now;
        }
    }
    public void setAdminNote(String adminNote) {this.adminNote = adminNote == null ? "" : adminNote.trim();}
    public UUID getId() {return id;}
    public UUID getMemberId() {return memberId;}
    public UUID getItemId() {return itemId;}
    public String getItemName() {return itemName;}
    public Long getPriceKrw() {return priceKrw;}
    public String getSizeLabel() {return sizeLabel;}
    public LocalDate getPickupDate() {return pickupDate;}
    public LocalDate getReturnDate() {return returnDate;}
    public RentalStatus getStatus() {return status;}
    public String getCustomerNote() {return customerNote;}
    public String getAdminNote() {return adminNote;}
    public CancelledBy getCancelledBy() {return cancelledBy;}
    public String getCancelReason() {return cancelReason;}
    public Instant getConfirmedAt() {return confirmedAt;}
    public Instant getPickedUpAt() {return pickedUpAt;}
    public Instant getReturnedAt() {return returnedAt;}
    public Instant getCancelledAt() {return cancelledAt;}
    public Instant getCreatedAt() {return createdAt;}
}