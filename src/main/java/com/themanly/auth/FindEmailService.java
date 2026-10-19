package com.themanly.auth;

import com.themanly.common.error.DomainRuleException;
import com.themanly.member.MemberRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FindEmailService {

    private static final Logger log = LoggerFactory.getLogger(FindEmailService.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MAX_RESULTS = 5;
    private static final int LIMIT = 10;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final int MAX_TRACKED = 50_000;

    private final MemberRepository members;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public FindEmailService(MemberRepository members) {
        this.members = members;
    }

    public record Account(String maskedEmail, LocalDate joinedOn) {
    }

    private record Window(Instant start, int count) {
    }

    @Transactional(readOnly = true)
    public List<Account> find(String name, String phone, String client) {
        allow(client, Instant.now());
        String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        if (n.isEmpty() || digits.length() < 10 || digits.length() > 11) {
            return List.of();
        }
        List<Account> found = members.findForEmailLookup(n, digits, PageRequest.of(0, MAX_RESULTS)).stream()
                .map(m -> new Account(mask(m.getEmail()), m.getCreatedAt().atZone(SEOUL).toLocalDate()))
                .toList();
        log.info("아이디 찾기 matched={}", found.size());
        return found;
    }

    void allow(String client, Instant now) {
        if (client == null || client.isBlank()) {
            return;
        }
        if (windows.size() > MAX_TRACKED) {
            windows.values().removeIf(w -> w.start().plus(WINDOW).isBefore(now));
        }
        Window w = windows.compute(client, (k, old) -> old == null || old.start().plus(WINDOW).isBefore(now)
                ? new Window(now, 1)
                : new Window(old.start(), old.count() + 1));
        if (w.count() > LIMIT) {
            throw new DomainRuleException("TOO_MANY_REQUESTS", "시도가 너무 잦습니다. 잠시 후 다시 시도해 주세요.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }
    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        int keep = local.length() <= 2 ? 1 : 2;
        return local.substring(0, keep) + "*".repeat(local.length() - keep) + email.substring(at);
    }
}