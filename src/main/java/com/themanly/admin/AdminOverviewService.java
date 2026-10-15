package com.themanly.admin;

import com.themanly.booking.Appointment;
import com.themanly.booking.AppointmentRepository;
import com.themanly.booking.AppointmentStatus;
import com.themanly.booking.BookingPolicy;
import com.themanly.common.error.ResourceNotFoundException;
import com.themanly.inquiry.Inquiry;
import com.themanly.inquiry.InquiryRepository;
import com.themanly.measurement.Measurement;
import com.themanly.measurement.MeasurementRepository;
import com.themanly.member.Member;
import com.themanly.member.MemberRepository;
import com.themanly.order.BespokeOrder;
import com.themanly.order.BespokeOrderRepository;
import com.themanly.order.OrderStage;
import com.themanly.order.OrderStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOverviewService {
    private final AppointmentRepository appointments;
    private final BespokeOrderRepository orders;
    private final MeasurementRepository measurements;
    private final MemberRepository members;
    private final InquiryRepository inquiries;
    public AdminOverviewService(AppointmentRepository appointments, BespokeOrderRepository orders, MeasurementRepository measurements, MemberRepository members, InquiryRepository inquiries) {
        this.appointments = appointments;
        this.orders = orders;
        this.measurements = measurements;
        this.members = members;
        this.inquiries = inquiries;}
    public record Dashboard(long todayAppointments, List<Appointment> todayList, long requestedAppointments, long inProgressOrders, Map<OrderStage, Long> ordersByStage, List<BespokeOrder> dueThisWeek, long openInquiries) { }
    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        LocalDate today = LocalDate.now(BookingPolicy.SEOUL);
        Instant dayStart = today.atStartOfDay(BookingPolicy.SEOUL).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(BookingPolicy.SEOUL).toInstant();
        List<Appointment> todayList = appointments.findByStartsAtBetweenAndStatusInOrderByStartsAtAsc(dayStart, dayEnd, AppointmentStatus.LIVE);
        Map<OrderStage, Long> byStage = new EnumMap<>(OrderStage.class);
        for (OrderStage s : OrderStage.values()) {byStage.put(s, 0L);}
        List<BespokeOrder> inProgress = orders.findByStatusOrderByLastEventAtDesc(OrderStatus.IN_PROGRESS);
        for (BespokeOrder o : inProgress) {byStage.merge(o.getStage(), 1L, Long::sum);}
        LocalDate weekEnd = today.plusDays(7);
        List<BespokeOrder> dueThisWeek = inProgress.stream().filter(o -> o.getDueOn() != null && !o.getDueOn().isBefore(today) && !o.getDueOn().isAfter(weekEnd)).sorted((a, b) -> a.getDueOn().compareTo(b.getDueOn())).toList();
        return new Dashboard(todayList.size(), todayList, appointments.countByStatus(AppointmentStatus.REQUESTED), inProgress.size(), byStage, dueThisWeek, inquiries.countByStatus(Inquiry.Status.OPEN));}
    public record MemberOverview(Member member, Measurement latestMeasurement, int measurementCount, List<BespokeOrder> orders, List<Appointment> upcoming) { }
    @Transactional(readOnly = true)
    public MemberOverview member(UUID memberId) {
        Member member = members.findById(memberId).orElseThrow(() -> new ResourceNotFoundException("회원 없음 id=" + memberId));
        List<Measurement> all = measurements.findByMemberIdOrderByMeasuredOnDescCreatedAtDesc(memberId);
        List<Appointment> upcoming = appointments.findByMemberIdAndStartsAtAfterAndStatusInOrderByStartsAtAsc(memberId,
                Instant.now(), AppointmentStatus.LIVE);
        return new MemberOverview(member, all.isEmpty() ? null : all.getFirst(), all.size(),
                orders.findByMemberIdOrderByCreatedAtDesc(memberId), upcoming);
    }
}