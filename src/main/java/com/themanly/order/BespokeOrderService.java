package com.themanly.order;

import com.themanly.catalog.Fabric;
import com.themanly.catalog.FabricRepository;
import com.themanly.catalog.StyleOption;
import com.themanly.catalog.StyleOptionRepository;
import com.themanly.common.error.DomainRuleException;
import com.themanly.common.error.ResourceNotFoundException;
import com.themanly.measurement.Measurement;
import com.themanly.measurement.MeasurementRepository;
import com.themanly.member.Member;
import com.themanly.member.MemberRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BespokeOrderService {

    private static final Logger log = LoggerFactory.getLogger(BespokeOrderService.class);
    static final int PAGE_SIZE = 20;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BespokeOrderRepository orders;
    private final MemberRepository members;
    private final FabricRepository fabrics;
    private final StyleOptionRepository styleOptions;
    private final MeasurementRepository measurements;
    private final ApplicationEventPublisher events;

    public BespokeOrderService(BespokeOrderRepository orders, MemberRepository members, FabricRepository fabrics,
                               StyleOptionRepository styleOptions, MeasurementRepository measurements,
                               ApplicationEventPublisher events) {
        this.orders = orders;
        this.members = members;
        this.fabrics = fabrics;
        this.styleOptions = styleOptions;
        this.measurements = measurements;
        this.events = events;
    }

    public record Input(Garment garment, String title, UUID fabricId, String fabricNote, UUID measurementId,
                        Map<StyleOption.Category, String> styles, Long priceKrw, Long depositKrw, LocalDate dueOn,
                        String adminNote) {
    }

    public record Described(BespokeOrder order, Member member, Fabric fabric, List<StyleOption> styles, LocalDate measuredOn) {
    }

    public record Row(BespokeOrder order, Member member) {
    }

    @Transactional(readOnly = true)
    public List<BespokeOrder> mine(UUID memberId) {
        return orders.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    @Transactional(readOnly = true)
    public Described mine(UUID memberId, String orderNumber) {
        BespokeOrder o = orders.findByOrderNumber(orderNumber).filter(x -> x.isOwnedBy(memberId))
                .orElseThrow(() -> new ResourceNotFoundException("주문 없음 orderNumber=" + orderNumber));
        return describe(o);
    }

    @Transactional(readOnly = true)
    public Page<Row> search(String query, OrderStatus status, OrderStage stage, int page) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String pattern = "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        Set<OrderStatus> statuses = status == null ? EnumSet.allOf(OrderStatus.class) : EnumSet.of(status);
        Set<OrderStage> stages = stage == null ? EnumSet.allOf(OrderStage.class) : EnumSet.of(stage);
        Page<BespokeOrder> found = orders.search(pattern, statuses, stages,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "lastEventAt")));
        Map<UUID, Member> byId = members.findAllById(found.getContent().stream().map(BespokeOrder::getMemberId).distinct().toList())
                .stream().collect(Collectors.toMap(Member::getId, Function.identity()));
        return found.map(o -> new Row(o, byId.get(o.getMemberId())));
    }

    @Transactional(readOnly = true)
    public List<Row> ofMember(UUID memberId) {
        Member member = members.findById(memberId).orElse(null);
        return orders.findByMemberIdOrderByCreatedAtDesc(memberId).stream().map(o -> new Row(o, member)).toList();
    }

    @Transactional(readOnly = true)
    public Described get(String orderNumber) {
        return describe(find(orderNumber));
    }

    @Transactional
    public Described open(UUID memberId, Input in, String message) {
        Member member = members.findById(memberId).filter(Member::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("회원 없음 id=" + memberId));
        UUID measurementId = in.measurementId() != null ? in.measurementId()
                : measurements.findFirstByMemberIdOrderByMeasuredOnDescCreatedAtDesc(memberId).map(Measurement::getId).orElse(null);
        String number;
        do {
            number = newOrderNumber();
        } while (orders.existsByOrderNumber(number));
        BespokeOrder o = orders.save(BespokeOrder.open(number, member.getId(), details(memberId, in, measurementId), message,
                Instant.now()));
        log.info("맞춤 주문 시작 orderNumber={} memberId={}", number, memberId);
        publish(o, o.getEvents().getLast());
        return describe(o);
    }

    @Transactional
    public Described update(String orderNumber, Input in) {
        BespokeOrder o = find(orderNumber);
        o.change(details(o.getMemberId(), in, in.measurementId()));
        log.info("맞춤 주문 수정 orderNumber={}", orderNumber);
        return describe(o);
    }

    @Transactional
    public Described moveTo(String orderNumber, OrderStage stage, String message) {
        BespokeOrder o = find(orderNumber);
        if (o.getStatus() == OrderStatus.IN_PROGRESS && o.getStage() == stage) {
            throw new DomainRuleException("SAME_STAGE", "이미 " + stage.label() + " 단계입니다. 안내만 남기려면 안내 남기기를 써 주세요.");
        }
        publish(o, o.moveTo(stage, message, Instant.now()));
        log.info("맞춤 주문 단계 orderNumber={} stage={}", orderNumber, stage);
        return describe(o);
    }

    @Transactional
    public Described note(String orderNumber, String message) {
        if (message == null || message.isBlank()) {
            throw DomainRuleException.badRequest("EMPTY_MESSAGE", "안내할 내용을 적어 주세요.");
        }
        BespokeOrder o = find(orderNumber);
        publish(o, o.note(message, Instant.now()));
        return describe(o);
    }

    @Transactional
    public Described complete(String orderNumber, String message) {
        BespokeOrder o = find(orderNumber);
        if (o.getStatus() == OrderStatus.IN_PROGRESS && o.getStage() != OrderStage.DELIVERY) {
            throw new DomainRuleException("NOT_DELIVERY", "수령 단계에서만 수령 완료로 바꿀 수 있습니다.");
        }
        publish(o, o.complete(message, Instant.now()));
        log.info("맞춤 주문 수령 완료 orderNumber={}", orderNumber);
        return describe(o);
    }

    @Transactional
    public Described cancel(String orderNumber, String reason) {
        BespokeOrder o = find(orderNumber);
        publish(o, o.cancel(reason, Instant.now()));
        log.info("맞춤 주문 취소 orderNumber={}", orderNumber);
        return describe(o);
    }

    private BespokeOrder find(String orderNumber) {
        return orders.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("주문 없음 orderNumber=" + orderNumber));
    }

    private BespokeOrder.Details details(UUID memberId, Input in, UUID measurementId) {
        if (in.fabricId() != null && !fabrics.existsById(in.fabricId())) {
            throw DomainRuleException.badRequest("UNKNOWN_FABRIC", "없는 원단입니다. 원단 목록에서 다시 골라 주세요.");
        }
        if (measurementId != null && measurements.findById(measurementId).filter(m -> m.isOwnedBy(memberId)).isEmpty()) {
            throw DomainRuleException.badRequest("UNKNOWN_MEASUREMENT", "이 손님의 치수 기록이 아닙니다.");
        }
        Map<StyleOption.Category, String> styles = new EnumMap<>(StyleOption.Category.class);
        if (in.styles() != null) {
            for (Map.Entry<StyleOption.Category, String> e : in.styles().entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue().isBlank()) {
                    continue;
                }
                String code = StyleOption.normalizeCode(e.getValue());
                if (!styleOptions.existsByCategoryAndCode(e.getKey(), code)) {
                    throw DomainRuleException.badRequest("UNKNOWN_STYLE", e.getKey().label() + " 에 없는 선택지입니다: " + code);
                }
                styles.put(e.getKey(), code);
            }
        }
        long deposit = in.depositKrw() == null ? 0 : in.depositKrw();
        if (in.priceKrw() != null && deposit > in.priceKrw()) {
            throw DomainRuleException.badRequest("DEPOSIT_OVER_PRICE", "계약금이 총액보다 클 수 없습니다.");
        }
        return new BespokeOrder.Details(in.garment(), in.title(), in.fabricId(), in.fabricNote(), measurementId, styles,
                in.priceKrw(), deposit, in.dueOn(), in.adminNote());
    }

    private Described describe(BespokeOrder o) {
        o.getEvents();
        Fabric fabric = o.getFabricId() == null ? null : fabrics.findById(o.getFabricId()).orElse(null);
        List<StyleOption> styles = o.getStyles().entrySet().stream()
                .map(e -> styleOptions.findByCategoryAndCode(e.getKey(), e.getValue()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        LocalDate measuredOn = o.getMeasurementId() == null ? null
                : measurements.findById(o.getMeasurementId()).map(Measurement::getMeasuredOn).orElse(null);
        return new Described(o, members.findById(o.getMemberId()).orElse(null), fabric, styles, measuredOn);
    }

    private void publish(BespokeOrder o, OrderEvent e) {
        events.publishEvent(new OrderNotifier.Event(o.getId(), e.getId()));
    }

    static String newOrderNumber() {
        StringBuilder sb = new StringBuilder("TM")
                .append(LocalDate.now(SEOUL).format(DateTimeFormatter.BASIC_ISO_DATE))
                .append('-');
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}