package com.themanly.inquiry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 손님 문의 (V8). 답하면 ANSWERED, 끝나면 CLOSED. 닫힌 문의에는 답하지 않는다. */
@Entity
@Table(name = "inquiry")
public class Inquiry {

    public enum Topic {
        GENERAL("일반 문의"), BOOKING("예약"), ORDER("진행 중인 주문"), FABRIC("원단 · 스타일"), OTHER("기타");

        private final String label;

        Topic(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Status {
        OPEN("답변 대기"), ANSWERED("답변 완료"), CLOSED("종료");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    @Id
    private UUID id;

    @Column(name = "member_id")
    private UUID memberId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Topic topic;

    @Column(nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    @Column
    private String answer;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Inquiry() {
        // JPA
    }

    public Inquiry(UUID memberId, String name, String email, String phone, Topic topic, String message) {
        this.id = UUID.randomUUID();
        this.memberId = memberId;
        this.name = Objects.requireNonNull(name, "name").trim();
        this.email = Objects.requireNonNull(email, "email").trim().toLowerCase(java.util.Locale.ROOT);
        this.phone = phone == null || phone.isBlank() ? null : phone.trim();
        this.topic = Objects.requireNonNull(topic, "topic");
        this.message = Objects.requireNonNull(message, "message").trim();
    }

    /** 답하기. 이미 답했어도 고쳐 다시 보낼 수 있다. 닫힌 문의는 안 된다. */
    public void answer(String answer, Instant now) {
        if (status == Status.CLOSED) {
            throw new IllegalStateException("닫힌 문의 id=" + id);
        }
        this.answer = Objects.requireNonNull(answer, "answer").trim();
        this.answeredAt = now;
        this.status = Status.ANSWERED;
    }

    public void close() {
        this.status = Status.CLOSED;
    }

    public boolean isOwnedBy(UUID memberId) {
        return this.memberId != null && this.memberId.equals(memberId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public Topic getTopic() {
        return topic;
    }

    public String getMessage() {
        return message;
    }

    public Status getStatus() {
        return status;
    }

    public String getAnswer() {
        return answer;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}