package com.themanly.film;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "film")
public class Film {
    @Id
    private UUID id;
    @Column(name = "frame_count", nullable =false)
    private int frameCount;
    @Column(nullable = false)
    private short fps;
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;
    @Column(name = "byte_size", nullable = false)
    private long byteSize;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_by")
    private UUID createdBy;
    @Column(name = "created_at" , nullable = false, updatable = false)
    private Instant createdAt;
    protected Film(){}
    public Film(UUID id, int frameCount, int fps, String originalFilename, long byteSize, UUID createdBy) {
        this.id = id;
        this.frameCount = frameCount;
        this.fps = (short) fps;
        this.originalFilename = originalFilename;
        this.byteSize = byteSize;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void activate() {this.active = true;}
    public void deactivate() {this.active = false;}
    public UUID getId() {return id;}
    public int getFrameCount() {return frameCount;}
    public short getFps() {return fps;}
    public String getOriginalFilename() {return originalFilename;}
    public long getByteSize() {return byteSize;}
    public boolean isActive() {return active;}
    public UUID getCreatedBy() {return createdBy;}
    public Instant getCreatedAt() {return createdAt;}
}
