package com.themanly.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bespoke_order_event")
public class OrderEvent {
    public enum Kind { CREATED, STAGE, NOTE, COMPLETED, CANCELLED }
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private BespokeOrder order;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Kind kind;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OrderStage stage;
    @Column(nullable = false, updatable = false)
    private String message;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    protected OrderEvent() {}
    OrderEvent(BespokeOrder order, Kind kind, OrderStage stage, String message, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.order = order;
        this.kind = kind;
        this.stage = stage;
        this.message = message == null ? "" : message.trim();
        this.createdAt = createdAt;
    }
    public UUID getId() {return id;}
    public Kind getKind() {return kind;}
    public OrderStage getStage() {return stage;}
    public String getMessage() {return message;}
    public Instant getCreatedAt() {return createdAt;}
}