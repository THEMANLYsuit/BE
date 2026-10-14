package com.themanly.booking;

import com.themanly.mail.Mailer;
import com.themanly.member.Member;
import com.themanly.member.MemberRepository;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 예약 알림 메일. 서비스가 {@link Event} 를 내면 <b>커밋된 뒤에</b> 보낸다 — 저장이 되돌려졌는데 "확정" 메일이 나가면 안 된다.
 * 메일이 실패해도 예약은 그대로다(로그만). 관리자 주소는 {@code app.notify.admin-emails}(쉼표로 여럿) — 비우면 관리자 메일은 없다.
 */
@Component
public class BookingNotifier {

    private static final Logger log = LoggerFactory.getLogger(BookingNotifier.class);
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E) HH:mm", Locale.KOREAN).withZone(ZoneId.of("Asia/Seoul"));
    private static final String FOOTER = "\n\n─────────────\nTHE MANLY 맞춤정장\n경기도 수원시 영통구 매영로 425번길 1 1층\n이 메일은 발신 전용입니다.\n";

    public enum Kind { REQUESTED, CONFIRMED, CANCELLED_BY_CUSTOMER, CANCELLED_BY_ADMIN, RESCHEDULED }

    public record Event(Kind kind, UUID appointmentId) {
    }

    private final AppointmentRepository appointments;
    private final MemberRepository members;
    private final Mailer mailer;
    private final String front;
    private final List<String> admins;

    public BookingNotifier(AppointmentRepository appointments, MemberRepository members, Mailer mailer,
                           @Value("${app.front.base-url}") String front, @Value("${app.notify.admin-emails:}") String admins) {
        this.appointments = appointments;
        this.members = members;
        this.mailer = mailer;
        this.front = front.endsWith("/") ? front.substring(0, front.length() - 1) : front;
        this.admins = Arrays.stream(admins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(Event e) {
        try {
            Appointment a = appointments.findById(e.appointmentId()).orElse(null);
            if (a == null) {
                return;
            }
            Member m = members.findById(a.getMemberId()).filter(Member::isActive).orElse(null);
            String when = WHEN.format(a.getStartsAt());
            String what = a.getType().label();
            String mine = "\n\n내 예약 " + front + "/mypage/";
            switch (e.kind()) {
                case REQUESTED -> {
                    toCustomer(m, what + " 예약을 접수했습니다", what + " 예약 신청을 받았습니다. 확인한 뒤 확정 메일을 보내 드립니다.\n\n"
                            + "일시  " + when + "\n장소  THE MANLY 수원 매장" + mine);
                    toAdmins("[예약 신청] " + when + " · " + what + " · " + name(m), detail(a, m));
                }
                case CONFIRMED -> toCustomer(m, what + " 예약이 확정되었습니다", "예약이 확정되었습니다. 매장에서 뵙겠습니다.\n\n"
                        + "일시  " + when + "\n장소  THE MANLY 수원 매장 (경기도 수원시 영통구 매영로 425번길 1 1층)\n"
                        + "오시기 어려워지면 내 예약에서 취소하거나 매장으로 연락해 주세요." + mine);
                case RESCHEDULED -> toCustomer(m, what + " 예약 시간이 바뀌었습니다", "예약 시간이 아래와 같이 바뀌었습니다.\n\n"
                        + "일시  " + when + "\n장소  THE MANLY 수원 매장\n\n맞지 않으면 매장으로 연락해 주세요." + mine);
                case CANCELLED_BY_CUSTOMER -> toAdmins("[예약 취소] " + when + " · " + what + " · " + name(m) + " (손님 취소)", detail(a, m));
                case CANCELLED_BY_ADMIN -> toCustomer(m, what + " 예약이 취소되었습니다", "죄송합니다. 아래 예약이 취소되었습니다.\n\n"
                        + "일시  " + when + (a.getCancelReason() == null ? "" : "\n사유  " + a.getCancelReason())
                        + "\n\n다른 시간으로 다시 예약해 주시거나 매장으로 연락해 주세요." + mine);
            }
        } catch (RuntimeException ex) {
            log.error("예약 알림 메일을 만들지 못함 kind={} id={}", e.kind(), e.appointmentId(), ex);
        }
    }

    private String detail(Appointment a, Member m) {
        return "일시  " + WHEN.format(a.getStartsAt()) + "\n목적  " + a.getType().label() + "\n손님  " + name(m)
                + (m == null || m.getPhone() == null ? "" : " · " + m.getPhone()) + (m == null ? "" : " · " + m.getEmail())
                + (a.getCustomerNote().isEmpty() ? "" : "\n메모  " + a.getCustomerNote())
                + "\n\n관리자 화면 " + front + "/admin/bookings/?date=" + a.getStartsAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
    }

    private static String name(Member m) {
        return m == null ? "탈퇴 회원" : m.getName();
    }

    private void toCustomer(Member m, String subject, String body) {
        if (m == null) {
            return;
        }
        mailer.send(m.getEmail(), "[THE MANLY] " + subject, m.getName() + "님,\n\n" + body + FOOTER);
    }

    private void toAdmins(String subject, String body) {
        admins.forEach(to -> mailer.send(to, subject, body));
    }
}
