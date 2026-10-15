package com.themanly.booking;

import com.themanly.common.error.DomainRuleException;
import com.themanly.common.error.ResourceNotFoundException;
import com.themanly.member.Member;
import com.themanly.member.MemberRepository;
import com.themanly.order.BespokeOrder;
import com.themanly.order.BespokeOrderRepository;
import com.themanly.order.OrderStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
 * 예약 — 손님의 신청 · 취소 · 내 예약, 관리자의 확정 · 취소 · 완료 · 불참 · 시간 옮기기.
 *
 * <p>한 손님이 살아 있는 예약을 {@value #MAX_LIVE_PER_MEMBER}개까지만 둘 수 있다 — 자리를 선점해 두는 걸 막는다.
 * 같은 시각의 살아 있는 예약은 하나다(서비스가 먼저 보고, DB 부분 유니크 인덱스가 마지막으로 막는다).
 * 알림 메일은 이벤트({@link BookingNotifier.Event})로 — 커밋된 뒤에 나간다.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    static final int MAX_LIVE_PER_MEMBER = 3;
    /** 빈 시간 조회 한 번에 볼 수 있는 날 수. */
    static final int MAX_AVAILABILITY_DAYS = 31;

    private final AppointmentRepository appointments;
    private final MemberRepository members;
    private final BookingPolicy policy;
    private final ApplicationEventPublisher events;
    private final BespokeOrderRepository orders;

    public BookingService(AppointmentRepository appointments, MemberRepository members, BookingPolicy policy,
                          ApplicationEventPublisher events, BespokeOrderRepository orders) {
        this.appointments = appointments;
        this.members = members;
        this.policy = policy;
        this.events = events;
        this.orders = orders;
    }

    /** 하루의 자리들. {@code available} — 손님이 지금 잡을 수 있는가(규칙 통과 · 비어 있음). */
    public record Slot(Instant startsAt, boolean available) {
    }

    public record DayAvailability(LocalDate date, boolean closed, List<Slot> slots) {
    }

    /** 빈 시간. 로그인 없이 본다 — 누가 예약했는지는 안 나간다(비었는지만). */
    @Transactional(readOnly = true)
    public List<DayAvailability> availability(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_AVAILABILITY_DAYS) {
            throw DomainRuleException.badRequest("INVALID_RANGE", "조회 기간은 " + MAX_AVAILABILITY_DAYS + "일 이내여야 합니다.");
        }
        Instant now = Instant.now();
        Instant rangeStart = from.atStartOfDay(BookingPolicy.SEOUL).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(BookingPolicy.SEOUL).toInstant();
        Set<Instant> taken = appointments.findByStartsAtBetweenAndStatusInOrderByStartsAtAsc(rangeStart, rangeEnd, AppointmentStatus.LIVE)
                .stream().map(Appointment::getStartsAt).collect(Collectors.toSet());

        List<DayAvailability> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            List<Slot> slots = policy.slotsOn(d).stream()
                    .map(s -> new Slot(s, !taken.contains(s) && policy.whyNotBookable(s, now) == null))
                    .toList();
            days.add(new DayAvailability(d, policy.isClosed(d), slots));
        }
        return days;
    }

    /** 손님 신청. {@code orderNumber} 가 있으면 그 손님의 진행 중 주문이어야 한다(가봉 · 수령 예약). */
    @Transactional
    public Appointment request(UUID memberId, AppointmentType type, Instant startsAt, String note, String orderNumber) {
        Instant now = Instant.now();
        String why = policy.whyNotBookable(startsAt, now);
        if (why != null) {
            throw DomainRuleException.badRequest("NOT_BOOKABLE", why);
        }
        if (appointments.countByMemberIdAndStatusIn(memberId, AppointmentStatus.LIVE) >= MAX_LIVE_PER_MEMBER) {
            throw new DomainRuleException("TOO_MANY_LIVE", "진행 중인 예약은 " + MAX_LIVE_PER_MEMBER + "개까지만 둘 수 있습니다. 지난 예약을 취소하거나 끝난 뒤 다시 신청해 주세요.");
        }
        BespokeOrder order = orderOf(memberId, orderNumber);
        if (appointments.existsByStartsAtAndStatusIn(startsAt, AppointmentStatus.LIVE)) {
            throw new DomainRuleException("SLOT_TAKEN", "그 시간은 이미 예약이 있습니다. 다른 시간을 골라 주세요.");
        }
        Appointment a = Appointment.request(memberId, type, startsAt, note);
        if (order != null) {
            a.linkOrder(order.getId());
        }
        Appointment created = appointments.save(a);
        appointments.flush(); // 같은 자리를 동시에 누른 경우 여기서 유니크 인덱스에 걸린다(409)
        log.info("예약 신청 id={} memberId={} type={} startsAt={}", created.getId(), memberId, type, startsAt);
        events.publishEvent(new BookingNotifier.Event(BookingNotifier.Kind.REQUESTED, created.getId()));
        return created;
    }

    /**
     * 관리자가 상담 중에 잡는 예약 — 바로 확정이고 손님에게 확정 메일이 간다.
     * 앞뒤 제한(24시간 · 60일)과 손님당 개수 제한은 없다. 자리 눈금 · 빈 자리는 본다.
     */
    @Transactional
    public WithMember createByAdmin(UUID memberId, AppointmentType type, Instant startsAt, String adminNote, String orderNumber) {
        Member member = members.findById(memberId).filter(Member::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("회원 없음 id=" + memberId));
        if (!policy.isSlot(startsAt)) {
            throw DomainRuleException.badRequest("NOT_BOOKABLE", "영업 시간 안의 자리 시각이어야 합니다.");
        }
        if (appointments.existsByStartsAtAndStatusIn(startsAt, AppointmentStatus.LIVE)) {
            throw new DomainRuleException("SLOT_TAKEN", "그 시간은 이미 다른 예약이 있습니다.");
        }
        BespokeOrder order = orderOf(memberId, orderNumber);
        Appointment a = Appointment.byAdmin(member.getId(), type, startsAt, adminNote, Instant.now());
        if (order != null) {
            a.linkOrder(order.getId());
        }
        Appointment created = appointments.save(a);
        appointments.flush();
        log.info("예약 생성(관리자) id={} memberId={} type={} startsAt={}", created.getId(), memberId, type, startsAt);
        events.publishEvent(new BookingNotifier.Event(BookingNotifier.Kind.CONFIRMED, created.getId()));
        return new WithMember(created, member);
    }

    /** 한 주문에 묶인 예약 전부(취소 포함) — 관리자 주문 상세. */
    @Transactional(readOnly = true)
    public List<Appointment> ofOrder(UUID orderId) {
        return appointments.findByOrderIdOrderByStartsAtAsc(orderId);
    }

    /** 주문번호 → 그 손님의 진행 중 주문. 없거나 남의 것이거나 끝났으면 400. 비어 있으면 null. */
    private BespokeOrder orderOf(UUID memberId, String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            return null;
        }
        return orders.findByOrderNumber(orderNumber.trim())
                .filter(o -> o.isOwnedBy(memberId) && o.getStatus() == OrderStatus.IN_PROGRESS)
                .orElseThrow(() -> DomainRuleException.badRequest("UNKNOWN_ORDER", "진행 중인 맞춤 주문이 아닙니다."));
    }

    @Transactional(readOnly = true)
    public List<Appointment> mine(UUID memberId) {
        return appointments.findByMemberIdOrderByStartsAtDesc(memberId);
    }

    /** 손님 취소. 시작 전이면 언제든. 남의 예약은 "없다" 와 같은 답(404). */
    @Transactional
    public Appointment cancelByCustomer(UUID memberId, UUID id) {
        Appointment a = appointments.findById(id).filter(x -> x.isOwnedBy(memberId))
                .orElseThrow(() -> new ResourceNotFoundException("예약 없음 id=" + id));
        if (!a.isLive()) {
            throw new DomainRuleException("INVALID_STATE", "이미 취소되었거나 끝난 예약입니다.");
        }
        Instant now = Instant.now();
        if (!a.getStartsAt().isAfter(now)) {
            throw new DomainRuleException("INVALID_STATE", "시작한 예약은 취소할 수 없습니다. 매장으로 연락해 주세요.");
        }
        a.cancel(Appointment.CancelledBy.CUSTOMER, null, now);
        log.info("예약 취소(손님) id={}", id);
        events.publishEvent(new BookingNotifier.Event(BookingNotifier.Kind.CANCELLED_BY_CUSTOMER, id));
        return a;
    }

    // ── 관리자 ───────────────────────────────────────────

    public record WithMember(Appointment appointment, Member member) {
    }

    /** 기간 안의 예약. 상태가 없으면 전부. 회원 정보를 한 번에 붙인다(N+1 방지). */
    @Transactional(readOnly = true)
    public List<WithMember> list(LocalDate from, LocalDate to, AppointmentStatus status) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 92) {
            throw DomainRuleException.badRequest("INVALID_RANGE", "조회 기간은 3개월 이내여야 합니다.");
        }
        Set<AppointmentStatus> statuses = status == null ? EnumSet.allOf(AppointmentStatus.class) : EnumSet.of(status);
        List<Appointment> found = appointments.findByStartsAtBetweenAndStatusInOrderByStartsAtAsc(
                from.atStartOfDay(BookingPolicy.SEOUL).toInstant(), to.plusDays(1).atStartOfDay(BookingPolicy.SEOUL).toInstant(), statuses);
        return withMembers(found);
    }

    @Transactional(readOnly = true)
    public WithMember get(UUID id) {
        Appointment a = find(id);
        return new WithMember(a, members.findById(a.getMemberId()).orElse(null));
    }

    @Transactional
    public WithMember confirm(UUID id) {
        Appointment a = find(id);
        a.confirm(Instant.now());
        log.info("예약 확정 id={}", id);
        events.publishEvent(new BookingNotifier.Event(BookingNotifier.Kind.CONFIRMED, id));
        return get(id);
    }

    @Transactional
    public WithMember cancelByAdmin(UUID id, String reason) {
        Appointment a = find(id);
        a.cancel(Appointment.CancelledBy.ADMIN, reason, Instant.now());
        log.info("예약 취소(관리자) id={}", id);
        events.publishEvent(new BookingNotifier.Event(BookingNotifier.Kind.CANCELLED_BY_ADMIN, id));
        return get(id);
    }

    @Transactional
    public WithMember complete(UUID id) {
        find(id).complete();
        return get(id);
    }

    @Transactional
    public WithMember markNoShow(UUID id) {
        find(id).markNoShow();
        return get(id);
    }

    /** 시간 옮기기(관리자). 자리 눈금에 맞고 비어 있어야 한다. 앞뒤 제한(24시간 · 60일)은 관리자에게 없다. */
    @Transactional
    public WithMember reschedule(UUID id, Instant startsAt) {
        Appointment a = find(id);
        if (!policy.isSlot(startsAt)) {
            throw DomainRuleException.badRequest("NOT_BOOKABLE", "영업 시간 안의 자리 시각이어야 합니다.");
        }
        if (!startsAt.equals(a.getStartsAt()) && appointments.existsByStartsAtAndStatusIn(startsAt, AppointmentStatus.LIVE)) {
            throw new DomainRuleException("SLOT_TAKEN", "그 시간은 이미 다른 예약이 있습니다.");
        }
        a.reschedule(startsAt, Instant.now());
        appointments.flush();
        log.info("예약 시간 변경 id={} startsAt={}", id, startsAt);
        events.publishEvent(new BookingNotifier.Event(BookingNotifier.Kind.RESCHEDULED, id));
        return get(id);
    }

    @Transactional
    public WithMember note(UUID id, String adminNote) {
        find(id).setAdminNote(adminNote);
        return get(id);
    }

    private Appointment find(UUID id) {
        return appointments.findById(id).orElseThrow(() -> new ResourceNotFoundException("예약 없음 id=" + id));
    }

    private List<WithMember> withMembers(List<Appointment> found) {
        Map<UUID, Member> byId = members.findAllById(found.stream().map(Appointment::getMemberId).distinct().toList())
                .stream().collect(Collectors.toMap(Member::getId, Function.identity()));
        return found.stream().map(a -> new WithMember(a, byId.get(a.getMemberId()))).toList();
    }
}
