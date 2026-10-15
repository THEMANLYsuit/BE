package com.themanly.inquiry;

import com.themanly.mail.Mailer;
import java.util.Arrays;
import java.util.List;
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
 * 문의 메일 — 접수되면 손님에게 접수 확인 · 관리자에게 새 문의, 답하면 손님에게 답변.
 * 커밋된 뒤에 보낸다. 관리자 주소는 {@code app.notify.admin-emails} — 비우면 관리자 메일은 없다.
 */
@Component
public class InquiryNotifier {

    private static final Logger log = LoggerFactory.getLogger(InquiryNotifier.class);
    private static final String FOOTER = "\n\n─────────────\nTHE MANLY 맞춤정장\n경기도 수원시 영통구 매영로 425번길 1 1층\n이 메일은 발신 전용입니다.\n";

    public enum Kind { SUBMITTED, ANSWERED }

    public record Event(Kind kind, UUID inquiryId) {
    }

    private final InquiryRepository inquiries;
    private final Mailer mailer;
    private final String front;
    private final List<String> admins;

    public InquiryNotifier(InquiryRepository inquiries, Mailer mailer, @Value("${app.front.base-url}") String front,
                           @Value("${app.notify.admin-emails:}") String admins) {
        this.inquiries = inquiries;
        this.mailer = mailer;
        this.front = front.endsWith("/") ? front.substring(0, front.length() - 1) : front;
        this.admins = Arrays.stream(admins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(Event e) {
        try {
            Inquiry in = inquiries.findById(e.inquiryId()).orElse(null);
            if (in == null) {
                return;
            }
            String head = "문의 내용\n" + in.getMessage();
            switch (e.kind()) {
                case SUBMITTED -> {
                    mailer.send(in.getEmail(), "[THE MANLY] 문의를 받았습니다", in.getName() + "님,\n\n문의를 받았습니다. 확인한 뒤 이 메일 주소로 답해 드립니다."
                            + "\n\n" + head + (in.getMemberId() == null ? "" : "\n\n내 문의 " + front + "/mypage/#inquiries") + FOOTER);
                    admins.forEach(to -> mailer.send(to, "[문의] " + in.getTopic().label() + " · " + in.getName(),
                            "보낸 분  " + in.getName() + " · " + in.getEmail() + (in.getPhone() == null ? "" : " · " + in.getPhone())
                                    + "\n분류  " + in.getTopic().label() + "\n\n" + head
                                    + "\n\n관리자 화면 " + front + "/admin/inquiries/"));
                }
                case ANSWERED -> mailer.send(in.getEmail(), "[THE MANLY] 문의에 답해 드립니다", in.getName() + "님,\n\n" + in.getAnswer()
                        + "\n\n─ 보내신 " + head + (in.getMemberId() == null ? "" : "\n\n내 문의 " + front + "/mypage/#inquiries") + FOOTER);
            }
        } catch (RuntimeException ex) {
            log.error("문의 메일을 만들지 못함 kind={} id={}", e.kind(), e.inquiryId(), ex);
        }
    }
}