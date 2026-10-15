package com.themanly.order;

import com.themanly.mail.Mailer;
import com.themanly.member.Member;
import com.themanly.member.MemberRepository;
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
public class OrderNotifier {

    private static final Logger log = LoggerFactory.getLogger(OrderNotifier.class);
    private static final String FOOTER = "\n\n─────────────\nTHE MANLY 맞춤정장\n경기도 수원시 영통구 매영로 425번길 1 1층\n이 메일은 발신 전용입니다.\n";

    public record Event(UUID orderId, UUID eventId) {
    }

    private final BespokeOrderRepository orders;
    private final MemberRepository members;
    private final Mailer mailer;
    private final String front;

    public OrderNotifier(BespokeOrderRepository orders, MemberRepository members, Mailer mailer,
                         @Value("${app.front.base-url}") String front) {
        this.orders = orders;
        this.members = members;
        this.mailer = mailer;
        this.front = front.endsWith("/") ? front.substring(0, front.length() - 1) : front;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void on(Event e) {
        try {
            BespokeOrder o = orders.findById(e.orderId()).orElse(null);
            if (o == null) {
                return;
            }
            OrderEvent ev = o.getEvents().stream().filter(x -> x.getId().equals(e.eventId())).findFirst().orElse(null);
            Member m = members.findById(o.getMemberId()).filter(Member::isActive).orElse(null);
            if (ev == null || m == null) {
                return;
            }
            String no = o.getOrderNumber();
            String said = ev.getMessage().isEmpty() ? "" : "\n\n" + ev.getMessage();
            String link = "\n\n진행 상황 " + front + "/mypage/order/?no=" + no;
            switch (ev.getKind()) {
                case CREATED -> send(m, "맞춤 주문을 시작합니다", o.getTitle() + " 주문을 시작합니다.\n\n주문번호  " + no
                        + "\n품목  " + o.getGarment().label() + "\n지금 단계  " + ev.getStage().label() + said + link);
                case STAGE -> send(m, o.getTitle() + " — " + ev.getStage().label() + " 단계입니다", "진행 단계가 바뀌었습니다.\n\n"
                        + "주문번호  " + no + "\n지금 단계  " + ev.getStage().label() + said + link);
                case NOTE -> send(m, o.getTitle() + " — 안내드립니다", ev.getMessage() + "\n\n주문번호  " + no + link);
                case COMPLETED -> send(m, o.getTitle() + " — 수령이 끝났습니다", "찾아가 주셔서 감사합니다. 치수와 선택은 기록으로 남아, "
                        + "다음 한 벌은 상담 한 번으로 시작할 수 있습니다.\n\n주문번호  " + no + said + link);
                case CANCELLED -> send(m, o.getTitle() + " — 주문이 취소되었습니다", "주문이 취소되었습니다.\n\n주문번호  " + no
                        + (ev.getMessage().isEmpty() ? "" : "\n사유  " + ev.getMessage())
                        + "\n\n궁금한 점은 매장으로 연락해 주세요." + link);
            }
        } catch (RuntimeException ex) {
            log.error("맞춤 주문 알림 메일을 만들지 못함 orderId={} eventId={}", e.orderId(), e.eventId(), ex);
        }
    }

    private void send(Member m, String subject, String body) {
        mailer.send(m.getEmail(), "[THE MANLY] " + subject, m.getName() + "님,\n\n" + body + FOOTER);
    }
}