package com.themanly.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_access_log")
public class AdminAccessLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;
    @Column(name = "actor_email", nullable = false, updatable = false)
    private String actorEmail;
    @Column(nullable = false, updatable = false)
    private String method;
    @Column(nullable = false, updatable = false)
    private String path;
    @Column(nullable = false, updatable = false)
    private short status;
    @Column(updatable = false)
    private String ip;
    @Column(name = "user_agent", updatable = false)
    private String userAgent;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    protected AdminAccessLog() {
    }

    public AdminAccessLog(UUID actorId, String actorEmail, String method, String path, int status, String ip, String userAgent) {
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.method = method;
        this.path = cut(path, 500);
        this.status = (short) status;
        this.ip = cut(ip, 45);
        this.userAgent = cut(userAgent, 200);
        this.createdAt = Instant.now();
    }

    private static String cut(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
    public Long getId(){return id;}
    public String getUserAgent(){return userAgent;}
    public UUID getActorId(){return actorId;}
    public String getActorEmail(){return actorEmail;}
    public String getMethod(){return method;}
    public String getPath(){return path;}
    public short getStatus(){return status;}
    public String getIp(){return ip;}
    public Instant getCreatedAt(){return createdAt;}
}
