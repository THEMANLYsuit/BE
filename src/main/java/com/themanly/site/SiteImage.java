package com.themanly.site;

import com.themanly.media.MediaAsset;
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
import java.util.Objects;

/** 메인 사진 칸 (V16). 칸마다 올린 이미지 한 장과 대체 텍스트. 비어 있는 칸은 줄이 없다. */
@Entity
@Table(name = "site_image")
public class SiteImage {

    public enum Slot {
        BODIES,
        SUITS,
        EMBLEM,
        PROCESS,
        SHOP
    }

    @Id
    @Enumerated(EnumType.STRING)
    private Slot slot;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private MediaAsset media;
    @Column(nullable = false)
    private String alt;
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
    protected SiteImage() {}
    public SiteImage(Slot slot, MediaAsset media, String alt) {
        this.slot = Objects.requireNonNull(slot, "slot");
        change(media, alt);
    }

    public void change(MediaAsset media, String alt) {
        this.media = Objects.requireNonNull(media, "media");
        this.alt = Objects.requireNonNull(alt, "alt").trim();
    }
    public Slot getSlot() {return slot;}
    public MediaAsset getMedia() {return media;}
    public String getAlt() {return alt;
    }public Instant getUpdatedAt() {return updatedAt;}
}