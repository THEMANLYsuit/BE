package com.themanly.admin.api;

import com.themanly.admin.AdminAccessLog;
import com.themanly.admin.AdminAccessLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 접속 · 열람 기록 조회 (V10). 최고 관리자만(SecurityConfig) — 관리자가 자기 기록을 지우거나 숨길 수 없어야 한다.
 * 조회도 /api/admin 아래라 이 조회 자체가 기록에 남는다.
 */
@RestController
@RequestMapping("/api/admin/access-logs")
public class AdminAccessLogController {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int PAGE_SIZE = 50;

    private final AdminAccessLogRepository logs;

    public AdminAccessLogController(AdminAccessLogRepository logs) {
        this.logs = logs;
    }

    /**
     * @param actorId 관리자 id (비우면 전체)
     * @param from    이 날부터 (한국 날짜, 포함)
     * @param to      이 날까지 (한국 날짜, 포함)
     * @param q       경로에 들어 있는 글자 — 주문번호 · 회원 id 로 "누가 이걸 봤나" 를 찾는다
     */
    @GetMapping
    public PageView list(@RequestParam(required = false) UUID actorId,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         @RequestParam(required = false) String q,
                         @RequestParam(defaultValue = "0") int page) {
        Instant fromAt = from == null ? null : from.atStartOfDay(SEOUL).toInstant();
        Instant toAt = to == null ? null : to.plusDays(1).atStartOfDay(SEOUL).toInstant();
        String path = q == null || q.isBlank() ? null : "%" + q.trim().replace("%", "\\%").replace("_", "\\_") + "%";
        Page<AdminAccessLog> found = logs.search(actorId, fromAt, toAt, path, PageRequest.of(Math.max(page, 0), PAGE_SIZE));
        return new PageView(found.getContent().stream().map(Row::of).toList(), found.getNumber(), found.getTotalPages(),
                found.getTotalElements());
    }

    public record Row(long id, UUID actorId, String actorEmail, String method, String path, int status, String ip,
                      Instant createdAt) {
        static Row of(AdminAccessLog l) {
            return new Row(l.getId(), l.getActorId(), l.getActorEmail(), l.getMethod(), l.getPath(), l.getStatus(), l.getIp(),
                    l.getCreatedAt());
        }
    }

    public record PageView(List<Row> items, int page, int totalPages, long total) {
    }
}