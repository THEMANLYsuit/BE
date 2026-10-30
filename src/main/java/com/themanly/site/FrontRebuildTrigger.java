package com.themanly.site;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class FrontRebuildTrigger {

    private static final Logger log = LoggerFactory.getLogger(FrontRebuildTrigger.class);
    public record SiteChanged(String reason) { }
    private final String hookUrl;
    private final Duration delay;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "front-rebuild");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean pending = new AtomicBoolean(false);

    public FrontRebuildTrigger(@Value("${app.front.deploy-hook-url:}") String hookUrl, @Value("${app.front.rebuild-delay:30s}") Duration delay) {
        this.hookUrl = hookUrl == null ? "" : hookUrl.trim();
        this.delay = delay;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(SiteChanged event) {
        if (hookUrl.isEmpty()) {
            log.debug("빌더 주소 없음 — 다시 빌드하지 않음 reason={}", event.reason());
            return;
        }
        if (pending.compareAndSet(false, true)) {
            scheduler.schedule(this::fire, delay.toMillis(), TimeUnit.MILLISECONDS);
            log.info("프론트 다시 빌드 예약 delay={} reason={}", delay, event.reason());
        }
    }
    public boolean isPending() {
        return pending.get();
    }

    private void fire() {
        pending.set(false);
        try {HttpResponse<Void> res = http.send(HttpRequest.newBuilder(URI.create(hookUrl)).timeout(Duration.ofSeconds(10)).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() / 100 == 2) {log.info("프론트 다시 빌드 요청 완료 status={}", res.statusCode());} else {log.error("프론트 다시 빌드 요청 거부 status={} — 손님 화면이 옛 내용으로 남아 있다", res.statusCode());}
        } catch (Exception e) {if (e instanceof InterruptedException) {Thread.currentThread().interrupt();}
            log.error("프론트 다시 빌드 요청 실패 — 손님 화면이 옛 내용으로 남아 있다", e);
        }
    }
    @PreDestroy
    void shutdown() {scheduler.shutdownNow();}
}