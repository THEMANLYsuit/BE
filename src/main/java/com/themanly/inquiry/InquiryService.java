package com.themanly.inquiry;

import com.themanly.common.error.DomainRuleException;
import com.themanly.common.error.ResourceNotFoundException;
import com.themanly.member.Member;
import com.themanly.member.MemberRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문의 — 손님이 보내고(회원 · 비회원), 관리자가 답한다. 보낼 때 관리자에게, 답할 때 손님에게 메일({@link InquiryNotifier}).
 * 회원이 보내면 이름 · 이메일은 가입 정보를 쓴다 — 남의 이름으로 보낼 수 없다.
 */
@Service
public class InquiryService {

    private static final Logger log = LoggerFactory.getLogger(InquiryService.class);
    static final int PAGE_SIZE = 20;

    private final InquiryRepository inquiries;
    private final MemberRepository members;
    private final ApplicationEventPublisher events;

    public InquiryService(InquiryRepository inquiries, MemberRepository members, ApplicationEventPublisher events) {
        this.inquiries = inquiries;
        this.members = members;
        this.events = events;
    }

    /** memberId 가 있으면 회원 문의(이름 · 이메일은 가입 정보), 없으면 비회원 문의(이름 · 이메일 필수). */
    @Transactional
    public Inquiry submit(UUID memberId, String name, String email, String phone, Inquiry.Topic topic, String message) {
        Inquiry in;
        if (memberId != null) {
            Member m = members.findById(memberId).filter(Member::isActive)
                    .orElseThrow(() -> new ResourceNotFoundException("회원 없음 id=" + memberId));
            in = new Inquiry(m.getId(), m.getName(), m.getEmail(), phone == null || phone.isBlank() ? m.getPhone() : phone, topic, message);
        } else {
            if (name == null || name.isBlank() || email == null || email.isBlank()) {
                throw DomainRuleException.badRequest("CONTACT_REQUIRED", "답을 받을 이름과 이메일을 적어 주세요.");
            }
            in = new Inquiry(null, name, email, phone, topic, message);
        }
        inquiries.save(in);
        log.info("문의 접수 id={} topic={} member={}", in.getId(), topic, memberId != null);
        events.publishEvent(new InquiryNotifier.Event(InquiryNotifier.Kind.SUBMITTED, in.getId()));
        return in;
    }

    @Transactional(readOnly = true)
    public List<Inquiry> mine(UUID memberId) {
        return inquiries.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    // ── 관리자 ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Inquiry> list(Inquiry.Status status, int page) {
        Set<Inquiry.Status> statuses = status == null ? EnumSet.allOf(Inquiry.Status.class) : EnumSet.of(status);
        return inquiries.findByStatusInOrderByCreatedAtDesc(statuses, PageRequest.of(Math.max(page, 0), PAGE_SIZE));
    }

    @Transactional(readOnly = true)
    public Inquiry get(UUID id) {
        return inquiries.findById(id).orElseThrow(() -> new ResourceNotFoundException("문의 없음 id=" + id));
    }

    @Transactional(readOnly = true)
    public long openCount() {
        return inquiries.countByStatus(Inquiry.Status.OPEN);
    }

    /** 답하기 — 손님에게 메일이 간다. 답을 고쳐 다시 보내도 된다. */
    @Transactional
    public Inquiry answer(UUID id, String answer) {
        if (answer == null || answer.isBlank()) {
            throw DomainRuleException.badRequest("EMPTY_ANSWER", "답변 내용을 적어 주세요.");
        }
        Inquiry in = get(id);
        in.answer(answer, Instant.now());
        log.info("문의 답변 id={}", id);
        events.publishEvent(new InquiryNotifier.Event(InquiryNotifier.Kind.ANSWERED, id));
        return in;
    }

    @Transactional
    public Inquiry close(UUID id) {
        Inquiry in = get(id);
        in.close();
        log.info("문의 종료 id={}", id);
        return in;
    }
}