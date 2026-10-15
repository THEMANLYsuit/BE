package com.themanly.admin.api;

import com.themanly.admin.AdminOverviewService;
import com.themanly.booking.Appointment;
import com.themanly.booking.AppointmentStatus;
import com.themanly.booking.AppointmentType;
import com.themanly.measurement.api.AdminMeasurementController;
import com.themanly.member.api.AdminMemberController;
import com.themanly.order.OrderStage;
import com.themanly.order.api.MyOrderController;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminOverviewController {
    private final AdminOverviewService overview;
    public AdminOverviewController(AdminOverviewService overview) {this.overview = overview;}
    @GetMapping("/dashboard")
    public Dashboard dashboard() {AdminOverviewService.Dashboard d = overview.dashboard();
        return new Dashboard(d.todayAppointments(), d.todayList().stream().map(BookingBrief::of).toList(), d.requestedAppointments(), d.inProgressOrders(), d.ordersByStage(), d.dueThisWeek().stream().map(MyOrderController.Summary::from).toList(), d.openInquiries());}
    @GetMapping("/members/{memberId}/overview")
    public MemberOverview member(@PathVariable UUID memberId) {AdminOverviewService.MemberOverview m = overview.member(memberId);
        return new MemberOverview(AdminMemberController.View.from(m.member()), m.latestMeasurement() == null ? null : AdminMeasurementController.AdminView.from(m.latestMeasurement()), m.measurementCount(), m.orders().stream().map(MyOrderController.Summary::from).toList(), m.upcoming().stream().map(BookingBrief::of).toList());}
    public record BookingBrief(UUID id, UUID memberId, AppointmentType type, String typeLabel, AppointmentStatus status, String statusLabel, Instant startsAt, UUID orderId) {
        static BookingBrief of(Appointment a) {
            return new BookingBrief(a.getId(), a.getMemberId(), a.getType(), a.getType().label(), a.getStatus(), a.getStatus().label(), a.getStartsAt(), a.getOrderId());}}
    public record Dashboard(long todayAppointments, List<BookingBrief> todayList, long requestedAppointments, long inProgressOrders, Map<OrderStage, Long> ordersByStage, List<MyOrderController.Summary> dueThisWeek, long openInquiries) { }
    public record MemberOverview(AdminMemberController.View member, AdminMeasurementController.AdminView latestMeasurement, int measurementCount, List<MyOrderController.Summary> orders, List<BookingBrief> upcoming) { }
}