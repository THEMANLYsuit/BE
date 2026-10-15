package com.themanly.order.api;

import com.themanly.catalog.StyleOption;
import com.themanly.member.Member;
import com.themanly.order.BespokeOrderService;
import com.themanly.order.Garment;
import com.themanly.order.OrderStage;
import com.themanly.order.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminOrderController {

    private final BespokeOrderService orders;

    public AdminOrderController(BespokeOrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/orders")
    public PageView list(@RequestParam(required = false) String q, @RequestParam(required = false) OrderStatus status,
                         @RequestParam(required = false) OrderStage stage, @RequestParam(defaultValue = "0") int page) {
        Page<BespokeOrderService.Row> found = orders.search(q, status, stage, page);
        return new PageView(found.getContent().stream().map(AdminRow::from).toList(), found.getNumber(), found.getTotalPages(),
                found.getTotalElements());
    }

    @GetMapping("/members/{memberId}/orders")
    public List<AdminRow> ofMember(@PathVariable UUID memberId) {
        return orders.ofMember(memberId).stream().map(AdminRow::from).toList();
    }

    @PostMapping("/members/{memberId}/orders")
    public ResponseEntity<AdminDetail> open(@PathVariable UUID memberId, @Valid @RequestBody OrderInput in) {
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminDetail.from(orders.open(memberId, in.toService(), in.message())));
    }

    @GetMapping("/orders/{orderNumber}")
    public AdminDetail get(@PathVariable String orderNumber) {
        return AdminDetail.from(orders.get(orderNumber));
    }

    @PutMapping("/orders/{orderNumber}")
    public AdminDetail update(@PathVariable String orderNumber, @Valid @RequestBody OrderInput in) {
        return AdminDetail.from(orders.update(orderNumber, in.toService()));
    }

    @PostMapping("/orders/{orderNumber}/stage")
    public AdminDetail moveTo(@PathVariable String orderNumber, @Valid @RequestBody StageInput in) {
        return AdminDetail.from(orders.moveTo(orderNumber, in.stage(), in.message()));
    }

    @PostMapping("/orders/{orderNumber}/notes")
    public AdminDetail note(@PathVariable String orderNumber, @Valid @RequestBody MessageInput in) {
        return AdminDetail.from(orders.note(orderNumber, in.message()));
    }

    @PostMapping("/orders/{orderNumber}/complete")
    public AdminDetail complete(@PathVariable String orderNumber, @Valid @RequestBody MessageInput in) {
        return AdminDetail.from(orders.complete(orderNumber, in.message()));
    }

    @PostMapping("/orders/{orderNumber}/cancel")
    public AdminDetail cancel(@PathVariable String orderNumber, @Valid @RequestBody CancelInput in) {
        return AdminDetail.from(orders.cancel(orderNumber, in.reason()));
    }

    public record OrderInput(@NotNull Garment garment, @NotBlank @Size(max = 80) String title, UUID fabricId,
                             @Size(max = 200) String fabricNote, UUID measurementId, Map<StyleOption.Category, String> styles,
                             @PositiveOrZero Long priceKrw, @PositiveOrZero Long depositKrw, LocalDate dueOn,
                             @Size(max = 2000) String adminNote, @Size(max = 1000) String message) {
        BespokeOrderService.Input toService() {
            return new BespokeOrderService.Input(garment, title, fabricId, fabricNote, measurementId, styles, priceKrw, depositKrw,
                    dueOn, adminNote);
        }
    }

    public record StageInput(@NotNull OrderStage stage, @Size(max = 1000) String message) {
    }

    public record MessageInput(@Size(max = 1000) String message) {
    }

    public record CancelInput(@Size(max = 300) String reason) {
    }

    public record Customer(UUID id, String name, String email, String phone) {
        static Customer of(Member m) {
            return m == null ? null : new Customer(m.getId(), m.getName(), m.getEmail(), m.getPhone());
        }
    }

    public record AdminRow(MyOrderController.Summary summary, Customer customer, Long priceKrw, long depositKrw) {
        static AdminRow from(BespokeOrderService.Row r) {
            return new AdminRow(MyOrderController.Summary.from(r.order()), Customer.of(r.member()), r.order().getPriceKrw(),
                    r.order().getDepositKrw());
        }
    }

    public record AdminDetail(MyOrderController.Detail order, Customer customer, UUID fabricId, UUID measurementId,
                              Map<StyleOption.Category, String> styleCodes, String adminNote) {
        static AdminDetail from(BespokeOrderService.Described d) {
            return new AdminDetail(MyOrderController.Detail.from(d), Customer.of(d.member()), d.order().getFabricId(),
                    d.order().getMeasurementId(), d.order().getStyles(), d.order().getAdminNote());
        }
    }

    public record PageView(List<AdminRow> items, int page, int totalPages, long total) {
    }
}