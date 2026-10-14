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
 * 스타일 선택지 (V3). 같은 묶음({@link Category}) 안에서 하나를 고른다 — 예) LAPEL: 노치 · 피크 · 숄.
 * 묶음마다 기본값({@code defaultChoice})은 하나다(DB 부분 유니크 인덱스).
 */
@Entity
@Table(name = "style_option")
public class StyleOption {

    /** 묶음. 이름은 V3 의 CHECK 와 같아야 한다. 표시 이름은 {@link #label()}. */
    public enum Category {
        JACKET_BUTTONS("재킷 단추"),
        LAPEL("라펠"),
        VENT("벤트"),
        POCKET("포켓"),
        LINING("안감"),
        TROUSER_PLEAT("바지 주름"),
        TROUSER_HEM("바짓단"),
        WAIST("허리"),
        MONOGRAM("이니셜");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Details(String name, String description, String imageUrl, boolean defaultChoice, boolean enabled) {
    }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "is_default", nullable = false)
    private boolean defaultChoice;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected StyleOption() {
        // JPA
    }

    public StyleOption(UUID id, Category category, String code, int sortOrder) {
        this.id = Objects.requireNonNull(id, "id");
        this.category = Objects.requireNonNull(category, "category");
        this.code = normalizeCode(code);
        this.sortOrder = sortOrder;
        this.name = "";
        this.description = "";
    }

    public static String normalizeCode(String raw) {
        return Objects.requireNonNull(raw, "code").trim().toLowerCase(Locale.ROOT);
    }

    public void change(Details d) {
        this.name = d.name() == null ? "" : d.name().trim();
        this.description = d.description() == null ? "" : d.description().trim();
        this.imageUrl = d.imageUrl() == null || d.imageUrl().isBlank() ? null : d.imageUrl().trim();
        this.defaultChoice = d.defaultChoice();
        this.enabled = d.enabled();
    }

    /** 다른 선택지가 기본값이 될 때 이쪽의 기본값 표시를 내린다. */
    void clearDefault() {
        this.defaultChoice = false;
    }

    public void moveTo(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public UUID getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public boolean isDefaultChoice() {
        return defaultChoice;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
