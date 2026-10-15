package com.themanly.order;

import com.themanly.catalog.StyleOption;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

@Entity
@Table(name = "bespoke_order")
public class BespokeOrder {
    public record Details(Garment garment, String title, UUID fabricId, String fabricNote, UUID measurementId, Map<StyleOption.Category, String> styles, Long priceKrw, long depositKrw, LocalDate dueOn, String adminNote) { }
    @Id
    private UUID id;
    @Column(name = "order_number", nullable = false, unique = true, updatable = false)
    private String orderNumber;
    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Garment garment;
    @Column(nullable = false)
    private String title;
    @Column(name = "fabric_id")
    private UUID fabricId;
    @Column(name = "fabric_note", nullable = false)
    private String fabricNote;
    @Column(name = "measurement_id")
    private UUID measurementId;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "bespoke_order_style", joinColumns = @JoinColumn(name = "order_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "category")
    @Column(name = "option_code", nullable = false)
    private Map<StyleOption.Category, String> styles = new HashMap<>();
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStage stage = OrderStage.CONSULTATION;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.IN_PROGRESS;
    @Column(name = "price_krw")
    private Long priceKrw;
    @Column(name = "deposit_krw", nullable = false)
    private long depositKrw;
    @Column(name = "due_on")
    private LocalDate dueOn;
    @Column(name = "admin_note", nullable = false)
    private String adminNote;
    @Column(name = "cancel_reason")
    private String cancelReason;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<OrderEvent> events = new ArrayList<>();
    protected BespokeOrder() {}
    public static BespokeOrder open(String orderNumber, UUID memberId, Details details, String message, Instant now) {
        BespokeOrder o = new BespokeOrder();
        o.id = UUID.randomUUID();
        o.orderNumber = Objects.requireNonNull(orderNumber, "orderNumber");
        o.memberId = Objects.requireNonNull(memberId, "memberId");
        o.change(details);
        o.record(OrderEvent.Kind.CREATED, message, now);
        return o;}
    public void change(Details d) {
        if (status == OrderStatus.CANCELLED) {throw new IllegalStateException("취소된 주문 orderNumber=" + orderNumber);}
        this.garment = Objects.requireNonNull(d.garment(), "garment");
        this.title = Objects.requireNonNull(d.title(), "title").trim();
        this.fabricId = d.fabricId();
        this.fabricNote = d.fabricNote() == null ? "" : d.fabricNote().trim();
        this.measurementId = d.measurementId();
        this.styles.clear();
        this.styles.putAll(d.styles());
        this.priceKrw = d.priceKrw();
        this.depositKrw = d.depositKrw();
        this.dueOn = d.dueOn();
        this.adminNote = d.adminNote() == null ? "" : d.adminNote().trim();}
    public OrderEvent moveTo(OrderStage next, String message, Instant now) {requireInProgress();this.stage = Objects.requireNonNull(next, "stage");return record(OrderEvent.Kind.STAGE, message, now);}
    public OrderEvent note(String message, Instant now) {if (status == OrderStatus.CANCELLED) {throw new IllegalStateException("취소된 주문 orderNumber=" + orderNumber);}return record(OrderEvent.Kind.NOTE, message, now);}
    public OrderEvent complete(String message, Instant now) {
        requireInProgress();
        if (stage != OrderStage.DELIVERY) {throw new IllegalStateException("수령 단계가 아님 stage=" + stage);}
        this.status = OrderStatus.COMPLETED;
        this.completedAt = now;
        return record(OrderEvent.Kind.COMPLETED, message, now);
    }
    public OrderEvent cancel(String reason, Instant now) {
        requireInProgress();
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason == null || reason.isBlank() ? null : reason.trim();
        this.cancelledAt = now;
        return record(OrderEvent.Kind.CANCELLED, reason, now);}
    public boolean isOwnedBy(UUID memberId) {return this.memberId.equals(memberId);}
    private void requireInProgress() {if (status != OrderStatus.IN_PROGRESS) {throw new IllegalStateException("진행 중이 아님 status=" + status);}}
    private OrderEvent record(OrderEvent.Kind kind, String message, Instant now) {
        OrderEvent e = new OrderEvent(this, kind, stage, message, now);
        events.add(e);
        this.lastEventAt = now;
        return e;
    }
    public UUID getId() {return id;}
    public String getOrderNumber() {return orderNumber;}
    public UUID getMemberId() {return memberId;}
    public Garment getGarment() {return garment;}
    public String getTitle() {return title;}
    public UUID getFabricId() {return fabricId;}
    public String getFabricNote() {return fabricNote;}
    public UUID getMeasurementId() {return measurementId;}
    public SortedMap<StyleOption.Category, String> getStyles() {return new TreeMap<>(styles);}
    public OrderStage getStage() {return stage;}
    public OrderStatus getStatus() {return status;}
    public Long getPriceKrw() {return priceKrw;}
    public long getDepositKrw() {return depositKrw;}
    public Long getBalanceKrw() {return priceKrw == null ? null : priceKrw - depositKrw;}
    public LocalDate getDueOn() {return dueOn;}
    public String getAdminNote() {return adminNote;}
    public String getCancelReason() {return cancelReason;}
    public Instant getCompletedAt() {return completedAt;}
    public Instant getCancelledAt() {return cancelledAt;}
    public Instant getLastEventAt() {return lastEventAt;}
    public Instant getCreatedAt() {return createdAt;}
    public List<OrderEvent> getEvents() {return List.copyOf(events);}
}