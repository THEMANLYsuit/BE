package com.themanly.measurement;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;

@Entity
@Table(name = "measurement")
public class Measurement {

    @Id
    private UUID id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    @Column(name = "measured_on", nullable = false)
    private LocalDate measuredOn;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "measurement_point", joinColumns = @JoinColumn(name = "measurement_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "point")
    @Column(name = "amount", nullable = false)
    private Map<MeasurementPoint, BigDecimal> points = new HashMap<>();

    @Column(name = "body_note", nullable = false)
    private String bodyNote;

    @Column(name = "admin_note", nullable = false)
    private String adminNote;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Measurement() {
    }

    public Measurement(UUID id, UUID memberId) {
        this.id = Objects.requireNonNull(id, "id");
        this.memberId = Objects.requireNonNull(memberId, "memberId");
        this.bodyNote = "";
        this.adminNote = "";
    }

    public void change(LocalDate measuredOn, Map<MeasurementPoint, BigDecimal> points, String bodyNote, String adminNote) {
        this.measuredOn = Objects.requireNonNull(measuredOn, "measuredOn");
        this.points.clear();
        this.points.putAll(points);
        this.bodyNote = bodyNote == null ? "" : bodyNote.trim();
        this.adminNote = adminNote == null ? "" : adminNote.trim();
    }

    public boolean isOwnedBy(UUID memberId) {
        return this.memberId.equals(memberId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public LocalDate getMeasuredOn() {
        return measuredOn;
    }

    public SortedMap<MeasurementPoint, BigDecimal> getPoints() {
        return new TreeMap<>(points);
    }

    public String getBodyNote() {
        return bodyNote;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}