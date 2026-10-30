package com.themanly.rental;

import com.themanly.booking.BookingPolicy;
import com.themanly.common.error.DomainRuleException;
import com.themanly.common.error.ResourceNotFoundException;
import com.themanly.member.Member;
import com.themanly.member.MemberRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대여 — 손님의 신청 · 취소 · 내 대여, 관리자의 확정 · 건넴 · 반납 · 취소 · 날짜 옮기기.
 *
 * <p>날짜 규칙: 수령일은 오늘부터 {@value #MIN_LEAD_DAYS}일 뒤 ~ {@value #MAX_AHEAD_DAYS}일 앞, 반납일은 수령일부터
 * 옷마다 정한 기간 안. 수령 · 반납은 매장에서 하므로 휴무일(BookingPolicy)은 안 된다.
 * 같은 옷이 그 날짜에 비어 있는지는 세지 않는다 — 관리자가 확인할 때 본다(재고 수가 정해지면 붙인다).
 * 알림 메일은 이벤트({@link RentalNotifier.Event})로 — 커밋된 뒤에 나간다.
 */
@Service
public class RentalService {

    private static final Logger log = LoggerFactory.getLogger(RentalService.class);
    static final int MIN_LEAD_DAYS = 2;
    static final int MAX_AHEAD_DAYS = 60;
    static final int MAX_LIVE_PER_MEMBER = 3;

    private final RentalRepository rentals;
    private final RentalItemRepository items;
    private final MemberRepository members;
    private final BookingPolicy policy;
    private final ApplicationEventPublisher events;

    public RentalService(RentalRepository rentals, RentalItemRepository items, MemberRepository members,
                         BookingPolicy policy, ApplicationEventPublisher events) {
        this.rentals = rentals;
        this.items = items;
        this.members = members;
        this.policy = policy;
        this.events = events;
    }

    /** 손님 신청. 신청 못 하는 옷 · 없는 사이즈 · 날짜 규칙 위반은 막는다. */
    @Transactional
    public Rental request(UUID memberId, UUID itemId, String size, LocalDate pickupDate, LocalDate returnDate, String note) {
        RentalItem item = items.findById(itemId).filter(RentalItem::isVisible)
                .orElseThrow(() -> new ResourceNotFoundException("대여 옷 없음 id=" + itemId));
        if (!item.isRentable()) {
            throw new DomainRuleException("NOT_RENTABLE", "지금은 대여할 수 없는 옷입니다. 매장으로 문의해 주세요.");
        }
        if (!item.accepts(size)) {
            throw DomainRuleException.badRequest("UNKNOWN_SIZE", "사이즈를 목록에서 골라 주세요.");
        }
        LocalDate today = LocalDate.now(BookingPolicy.SEOUL);
        if (pickupDate.isBefore(today.plusDays(MIN_LEAD_DAYS))) {
            throw DomainRuleException.badRequest("INVALID_DATES", "수령일은 오늘부터 " + MIN_LEAD_DAYS + "일 뒤부터 고를 수 있습니다. 급하시면 매장으로 전화해 주세요.");
        }
        if (pickupDate.isAfter(today.plusDays(MAX_AHEAD_DAYS))) {
            throw DomainRuleException.badRequest("INVALID_DATES", "대여는 " + MAX_AHEAD_DAYS + "일 앞까지만 받습니다.");
        }
        checkDates(item, pickupDate, returnDate);
        if (rentals.countByMemberIdAndStatusIn(memberId, RentalStatus.LIVE) >= MAX_LIVE_PER_MEMBER) {
            throw new DomainRuleException("TOO_MANY_LIVE", "진행 중인 대여는 " + MAX_LIVE_PER_MEMBER + "건까지만 둘 수 있습니다.");
        }
        Rental created = rentals.save(Rental.request(memberId, item, size, pickupDate, returnDate, note));
        log.info("대여 신청 id={} memberId={} itemId={} pickup={} return={}", created.getId(), memberId, itemId, pickupDate, returnDate);
        events.publishEvent(new RentalNotifier.Event(RentalNotifier.Kind.REQUESTED, created.getId()));
        return created;
    }

    /** 반납일은 수령일 이후 · 옷의 기간 안, 둘 다 영업일. 관리자 날짜 옮기기도 같은 규칙. */
    private void checkDates(RentalItem item, LocalDate pickupDate, LocalDate returnDate) {
        if (returnDate.isBefore(pickupDate)) {
            throw DomainRuleException.badRequest("INVALID_DATES", "반납일은 수령일과 같거나 뒤여야 합니다.");
        }
        if (ChronoUnit.DAYS.between(pickupDate, returnDate) > item.getRentalDays()) {
            throw DomainRuleException.badRequest("INVALID_DATES", "이 옷은 최대 " + item.getRentalDays() + "일까지 빌릴 수 있습니다. 더 필요하시면 매장으로 문의해 주세요.");
        }
        if (policy.isClosed(pickupDate) || policy.isClosed(returnDate)) {
            throw DomainRuleException.badRequest("INVALID_DATES", "수령 · 반납은 매장 휴무일에 할 수 없습니다. 다른 날을 골라 주세요.");
        }
    }

    @Transactional(readOnly = true)
    public List<Rental> mine(UUID memberId) {
        return rentals.findByMemberIdOrderByPickupDateDesc(memberId);
    }

    /** 손님 취소. 옷을 받기 전, 수령일 전날까지. 남의 대여는 "없다" 와 같은 답(404). */
    @Transactional
    public Rental cancelByCustomer(UUID memberId, UUID id) {
        Rental r = rentals.findById(id).filter(x -> x.isOwnedBy(memberId))
                .orElseThrow(() -> new ResourceNotFoundException("대여 없음 id=" + id));
        if (!r.isBeforePickup()) {
            throw new DomainRuleException("INVALID_STATE", "이미 취소되었거나 옷을 받은 대여입니다.");
        }
        if (!r.getPickupDate().isAfter(LocalDate.now(BookingPolicy.SEOUL))) {
            throw new DomainRuleException("INVALID_STATE", "수령일 당일에는 취소할 수 없습니다. 매장으로 연락해 주세요.");
        }
        r.cancel(Rental.CancelledBy.CUSTOMER, null, Instant.now());
        log.info("대여 취소(손님) id={}", id);
        events.publishEvent(new RentalNotifier.Event(RentalNotifier.Kind.CANCELLED_BY_CUSTOMER, id));
        return r;
    }

    // ── 관리자 ───────────────────────────────────────────

    public record WithMember(Rental rental, Member member) {
    }

    /** 수령일이 기간 안인 대여. 상태가 없으면 전부. 회원 정보를 한 번에 붙인다(N+1 방지). */
    @Transactional(readOnly = true)
    public List<WithMember> list(LocalDate from, LocalDate to, RentalStatus status) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 92) {
            throw DomainRuleException.badRequest("INVALID_RANGE", "조회 기간은 3개월 이내여야 합니다.");
        }
        Set<RentalStatus> statuses = status == null ? EnumSet.allOf(RentalStatus.class) : EnumSet.of(status);
        List<Rental> found = rentals.findByPickupDateBetweenAndStatusInOrderByPickupDateAscCreatedAtAsc(from, to, statuses);
        Map<UUID, Member> byId = members.findAllById(found.stream().map(Rental::getMemberId).distinct().toList())
                .stream().collect(Collectors.toMap(Member::getId, Function.identity()));
        return found.stream().map(r -> new WithMember(r, byId.get(r.getMemberId()))).toList();
    }

    @Transactional(readOnly = true)
    public WithMember get(UUID id) {
        Rental r = find(id);
        return new WithMember(r, members.findById(r.getMemberId()).orElse(null));
    }

    @Transactional
    public WithMember confirm(UUID id) {
        find(id).confirm(Instant.now());
        log.info("대여 확정 id={}", id);
        events.publishEvent(new RentalNotifier.Event(RentalNotifier.Kind.CONFIRMED, id));
        return get(id);
    }

    @Transactional
    public WithMember pickUp(UUID id) {
        find(id).pickUp(Instant.now());
        log.info("대여 건넴 id={}", id);
        return get(id);
    }

    @Transactional
    public WithMember markReturned(UUID id) {
        find(id).markReturned(Instant.now());
        log.info("대여 반납 id={}", id);
        return get(id);
    }

    @Transactional
    public WithMember cancelByAdmin(UUID id, String reason) {
        find(id).cancel(Rental.CancelledBy.ADMIN, reason, Instant.now());
        log.info("대여 취소(관리자) id={}", id);
        events.publishEvent(new RentalNotifier.Event(RentalNotifier.Kind.CANCELLED_BY_ADMIN, id));
        return get(id);
    }

    /** 날짜 옮기기(관리자). 앞뒤 제한(2일 · 60일)은 없고, 기간 · 휴무 규칙은 본다. */
    @Transactional
    public WithMember reschedule(UUID id, LocalDate pickupDate, LocalDate returnDate) {
        Rental r = find(id);
        RentalItem item = items.findById(r.getItemId())
                .orElseThrow(() -> new ResourceNotFoundException("대여 옷 없음 id=" + r.getItemId()));
        checkDates(item, pickupDate, returnDate);
        r.reschedule(pickupDate, returnDate, Instant.now());
        log.info("대여 날짜 변경 id={} pickup={} return={}", id, pickupDate, returnDate);
        events.publishEvent(new RentalNotifier.Event(RentalNotifier.Kind.RESCHEDULED, id));
        return get(id);
    }

    @Transactional
    public WithMember note(UUID id, String adminNote) {
        find(id).setAdminNote(adminNote);
        return get(id);
    }

    private Rental find(UUID id) {
        return rentals.findById(id).orElseThrow(() -> new ResourceNotFoundException("대여 없음 id=" + id));
    }
}