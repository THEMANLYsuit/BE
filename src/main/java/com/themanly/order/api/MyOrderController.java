package com.themanly.order.api;

import com.themanly.auth.MemberPrincipal;
import com.themanly.catalog.Fabric;
import com.themanly.catalog.StyleOption;
import com.themanly.order.BespokeOrder;
import com.themanly.order.BespokeOrderService;
import com.themanly.order.Garment;
import com.themanly.order.OrderEvent;
import com.themanly.order.OrderStage;
import com.themanly.order.OrderStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/mine")
public class MyOrderController {

    private final BespokeOrderService orders;

    public MyOrderController(BespokeOrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    public List<Summary> list(@AuthenticationPrincipal MemberPrincipal principal) {
        return orders.mine(principal.getId()).stream().map(Summary::from).toList();
    }

    @GetMapping("/{orderNumber}")
    public Detail detail(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable String orderNumber) {
        return Detail.from(orders.mine(principal.getId(), orderNumber));
    }

    public record Summary(String orderNumber, String title, Garment garment, String garmentLabel, OrderStage stage,
                          String stageLabel, OrderStatus status, String statusLabel, LocalDate dueOn, Instant createdAt,
                          Instant lastEventAt) {
        public static Summary from(BespokeOrder o) {
            return new Summary(o.getOrderNumber(), o.getTitle(), o.getGarment(), o.getGarment().label(), o.getStage(),
                    o.getStage().label(), o.getStatus(), o.getStatus().label(), o.getDueOn(), o.getCreatedAt(), o.getLastEventAt());
        }
    }

    public record FabricRef(String code, String name, String mill, String composition, String imageUrl) {
        static FabricRef of(Fabric f) {
            return f == null ? null : new FabricRef(f.getCode(), f.getName(), f.getMill(), f.getComposition(), f.getImageUrl());
        }
    }

    public record StyleRef(StyleOption.Category category, String categoryLabel, String code, String name) {
        static StyleRef of(StyleOption o) {
            return new StyleRef(o.getCategory(), o.getCategory().label(), o.getCode(), o.getName());
        }
    }

    public record Step(OrderStage stage, String label, boolean done, boolean current) {
        static List<Step> of(BespokeOrder o) {
            return Arrays.stream(OrderStage.values())
                    .map(s -> new Step(s, s.label(),
                            o.getStatus() == OrderStatus.COMPLETED || s.ordinal() < o.getStage().ordinal(),
                            o.getStatus() == OrderStatus.IN_PROGRESS && s == o.getStage()))
                    .toList();
        }
    }

    public record TimelineItem(OrderEvent.Kind kind, OrderStage stage, String stageLabel, String message, Instant at) {
        static TimelineItem of(OrderEvent e) {
            return new TimelineItem(e.getKind(), e.getStage(), e.getStage().label(), e.getMessage(), e.getCreatedAt());
        }
    }

    public record Detail(Summary summary, FabricRef fabric, String fabricNote, List<StyleRef> styles, LocalDate measuredOn,
                         Long priceKrw, long depositKrw, Long balanceKrw, String cancelReason, List<Step> steps,
                         List<TimelineItem> timeline) {
        static Detail from(BespokeOrderService.Described d) {
            BespokeOrder o = d.order();
            return new Detail(Summary.from(o), FabricRef.of(d.fabric()), o.getFabricNote(),
                    d.styles().stream().map(StyleRef::of).toList(), d.measuredOn(), o.getPriceKrw(), o.getDepositKrw(),
                    o.getBalanceKrw(), o.getCancelReason(), Step.of(o),
                    o.getEvents().reversed().stream().map(TimelineItem::of).toList());
        }
    }
}