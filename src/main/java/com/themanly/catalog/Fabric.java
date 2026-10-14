package com.themanly.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 원단 (V3). 손님에게는 {@link Status#PUBLISHED} · {@link Status#SOLD_OUT} 만 보인다.
 * 가격은 "이 원단으로 수트 한 벌" 의 시작가({@code priceFromKrw})만 둔다 — 최종 금액은 상담에서 정한다.
 */
@Entity
@Table(name = "fabric")
public class Fabric {

    public enum Status {
        /** 준비 중 — 관리자만 본다 */
        DRAFT,
        /** 손님에게 보이고 고를 수 있다 */
        PUBLISHED,
        /** 보이지만 고를 수 없다 — 다시 들어오면 PUBLISHED 로 */
        SOLD_OUT
    }

    public enum Season { ALL, SS, FW }

    /** 관리자가 적는 값 전부. 코드 · 상태 · 순서는 따로 다룬다. */
    public record Details(String name, String mill, String composition, Short weightGsm, String color, String pattern,
                          Season season, Long priceFromKrw, String description, String imageUrl) {
    }

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String mill;

    @Column(nullable = false)
    private String composition;

    @Column(name = "weight_gsm")
    private Short weightGsm;

    @Column(nullable = false)
    private String color;

    @Column(nullable = false)
    private String pattern;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Season season = Season.ALL;

    @Column(name = "price_from_krw")
    private Long priceFromKrw;

    @Column(nullable = false)
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Fabric() {
        // JPA
    }

    public Fabric(UUID id, String code, int sortOrder) {
        this.id = Objects.requireNonNull(id, "id");
        this.code = normalizeCode(code);
        this.sortOrder = sortOrder;
        this.name = "";
        this.mill = "";
        this.composition = "";
        this.color = "";
        this.pattern = "";
        this.description = "";
    }

    /** 원단 번호는 대문자로 통일한다 — 원단사 번호는 사람마다 대소문자를 다르게 적는다. */
    public static String normalizeCode(String raw) {
        return Objects.requireNonNull(raw, "code").trim().toUpperCase(Locale.ROOT);
    }

    public void change(Details d) {
        this.name = clean(d.name());
        this.mill = clean(d.mill());
        this.composition = clean(d.composition());
        this.weightGsm = d.weightGsm();
        this.color = clean(d.color());
        this.pattern = clean(d.pattern());
        this.season = d.season() == null ? Season.ALL : d.season();
        this.priceFromKrw = d.priceFromKrw();
        this.description = clean(d.description());
        this.imageUrl = d.imageUrl() == null || d.imageUrl().isBlank() ? null : d.imageUrl().trim();
    }

    public void changeStatus(Status status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public void moveTo(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** 손님에게 보이는가. */
    public boolean isVisible() {
        return status != Status.DRAFT;
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getMill() {
        return mill;
    }

    public String getComposition() {
        return composition;
    }

    public Short getWeightGsm() {
        return weightGsm;
    }

    public String getColor() {
        return color;
    }

    public String getPattern() {
        return pattern;
    }

    public Season getSeason() {
        return season;
    }

    public Long getPriceFromKrw() {
        return priceFromKrw;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Status getStatus() {
        return status;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
