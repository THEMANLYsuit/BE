package com.themanly.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 상담 · 가봉 · 수령 예약 (V4). 상태가 바뀔 수 있는지는 이 클래스가 판단한다 — 서비스는 순서(메일 · 잠금)만 지킨다.
 * 지난 예약은 지우지 않는다. 손님 이력이다.
 */
@Entity
@Table(name = "appointment")
public class Appointment {

    public enum CancelledBy { CUSTOMER, ADMIN }

    @Id
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.REQUESTED;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "duration_minutes", nullable = false)
    private short durationMinutes;

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

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Appointment() {
        // JPA
    }

    /** 손님의 신청. 자리 길이는 목적이 정한다. */
    public static Appointment request(UUID memberId, AppointmentType type, Instant startsAt, String customerNote) {
        Appointment a = new Appointment();
        a.id = UUID.randomUUID();
        a.memberId = Objects.requireNonNull(memberId, "memberId");
        a.type = Objects.requireNonNull(type, "type");
        a.startsAt = Objects.requireNonNull(startsAt, "startsAt");
        a.durationMinutes = (short) type.durationMinutes();
        a.customerNote = customerNote == null ? "" : customerNote.trim();
        a.adminNote = "";
        return a;
    }

    public boolean isOwnedBy(UUID memberId) {
        return this.memberId.equals(memberId);
    }

    public boolean isLive() {
        return status.isLive();
    }

    public Instant endsAt() {
        return startsAt.plusSeconds(durationMinutes * 60L);
    }

    public void confirm(Instant now) {
        if (status != AppointmentStatus.REQUESTED) {
            throw new IllegalStateException("확정할 수 없는 상태 status=" + status);
        }
        this.status = AppointmentStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    public void cancel(CancelledBy by, String reason, Instant now) {
        if (!isLive()) {
            throw new IllegalStateException("취소할 수 없는 상태 status=" + status);
        }
        this.status = AppointmentStatus.CANCELLED;
        this.cancelledBy = Objects.requireNonNull(by, "by");
        this.cancelReason = reason == null || reason.isBlank() ? null : reason.trim();
        this.cancelledAt = now;
    }

    public void complete() {
        if (status != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("완료할 수 없는 상태 status=" + status);
        }
        this.status = AppointmentStatus.COMPLETED;
    }

    public void markNoShow() {
        if (status != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("불참 처리할 수 없는 상태 status=" + status);
        }
        this.status = AppointmentStatus.NO_SHOW;
    }

    /** 시간 옮기기(관리자). 살아 있는 예약만. 신청 상태였으면 옮긴 김에 확정한다 — 관리자가 시간을 정했으니. */
    public void reschedule(Instant startsAt, Instant now) {
        if (!isLive()) {
            throw new IllegalStateException("옮길 수 없는 상태 status=" + status);
        }
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
        if (status == AppointmentStatus.REQUESTED) {
            this.status = AppointmentStatus.CONFIRMED;
            this.confirmedAt = now;
        }
    }

    public void setAdminNote(String adminNote) {
        this.adminNote = adminNote == null ? "" : adminNote.trim();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public AppointmentType getType() {
        return type;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public short getDurationMinutes() {
        return durationMinutes;
    }

    public String getCustomerNote() {
        return customerNote;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public CancelledBy getCancelledBy() {
        return cancelledBy;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
