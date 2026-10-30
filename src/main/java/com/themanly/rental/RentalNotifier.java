package com.themanly.rental;

import com.themanly.mail.Mailer;
import com.themanly.member.Member;
import com.themanly.member.MemberRepository;
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

@Component
public class RentalNotifier {

    private static final Logger log = LoggerFactory.getLogger(RentalNotifier.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN);
    private static final String FOOTER = "\n\n─────────────\nTHE MANLY 맞춤정장\n경기도 수원시 영통구 매영로 425번길 1 1층\n이 메일은 발신 전용입니다.\n";
    public enum Kind { REQUESTED, CONFIRMED, CANCELLED_BY_CUSTOMER, CANCELLED_BY_ADMIN, RESCHEDULED }
    public record Event(Kind kind, UUID rentalId) {}
    private final RentalRepository rentals;
    private final MemberRepository members;
    private final Mailer mailer;
    private final String front;
    private final List<String> admins;
    public RentalNotifier(RentalRepository rentals, MemberRepository members, Mailer mailer, @Value("${app.front.base-url}") String front, @Value("${app.notify.admin-emails:}") String admins) {
        this.rentals = rentals;
        this.members = members;
        this.mailer = mailer;
        this.front = front.endsWith("/") ? front.substring(0, front.length() - 1) : front;
        this.admins = Arrays.stream(admins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(Event e) {
        try {
            Rental r = rentals.findById(e.rentalId()).orElse(null);
            if (r == null) {return;}
            Member m = members.findById(r.getMemberId()).filter(Member::isActive).orElse(null);
            String what = r.getItemName() + (r.getSizeLabel().isEmpty() ? "" : " · " + r.getSizeLabel());
            String when = "수령  " + DAY.format(r.getPickupDate()) + "\n반납  " + DAY.format(r.getReturnDate());
            String mine = "\n\n내 대여 " + front + "/mypage/";
            switch (e.kind()) {
                case REQUESTED -> {toCustomer(m, "대여 신청을 접수했습니다", "대여 신청을 받았습니다. 확인한 뒤 확정 메일을 보내 드립니다.\n\n" + "옷  " + what + "\n" + when + "\n장소  THE MANLY 수원 매장" + mine);toAdmins("[대여 신청] " + DAY.format(r.getPickupDate()) + " · " + what + " · " + name(m), detail(r, m));}
                case CONFIRMED -> toCustomer(m, "대여가 확정되었습니다", "대여가 확정되었습니다. 수령일에 매장에서 옷을 건네 드립니다.\n" + "대여료 · 보증금은 매장에서 받습니다.\n\n옷  " + what + "\n" + when + "\n장소  THE MANLY 수원 매장 (경기도 수원시 영통구 매영로 425번길 1 1층)" + mine);
                case RESCHEDULED -> toCustomer(m, "대여 날짜가 바뀌었습니다", "대여 날짜가 아래와 같이 바뀌었습니다.\n\n" + "옷  " + what + "\n" + when + "\n\n맞지 않으면 매장으로 연락해 주세요." + mine);
                case CANCELLED_BY_CUSTOMER -> toAdmins("[대여 취소] " + DAY.format(r.getPickupDate()) + " · " + what + " · " + name(m) + " (손님 취소)", detail(r, m));
                case CANCELLED_BY_ADMIN -> toCustomer(m, "대여가 취소되었습니다", "죄송합니다. 아래 대여가 취소되었습니다.\n\n" + "옷  " + what + "\n" + when + (r.getCancelReason() == null ? "" : "\n사유  " + r.getCancelReason()) + "\n\n다른 날짜로 다시 신청해 주시거나 매장으로 연락해 주세요." + mine);
            }
        } catch (RuntimeException ex) {log.error("대여 알림 메일을 만들지 못함 kind={} id={}", e.kind(), e.rentalId(), ex);
        }
    }

    private String detail(Rental r, Member m) {return "옷  " + r.getItemName() + (r.getSizeLabel().isEmpty() ? "" : " · " + r.getSizeLabel()) + "\n수령  " + DAY.format(r.getPickupDate()) + "\n반납  " + DAY.format(r.getReturnDate()) + "\n손님  " + name(m) + (m == null || m.getPhone() == null ? "" : " · " + m.getPhone()) + (m == null ? "" : " · " + m.getEmail()) + (r.getCustomerNote().isEmpty() ? "" : "\n메모  " + r.getCustomerNote()) + "\n\n관리자 화면 " + front + "/admin/rentals/";
    }
    private static String name(Member m) {return m == null ? "탈퇴 회원" : m.getName();}
    private void toCustomer(Member m, String subject, String body) {
        if (m == null) {return;}
        mailer.send(m.getEmail(), "[THE MANLY] " + subject, m.getName() + "님,\n\n" + body + FOOTER);
    }
    private void toAdmins(String subject, String body) {admins.forEach(to -> mailer.send(to, subject, body));}
}