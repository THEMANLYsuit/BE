package com.themanly.member;

import com.themanly.auth.PasswordPolicy;
import com.themanly.auth.SessionTerminator;
import com.themanly.booking.AppointmentRepository;
import com.themanly.booking.AppointmentStatus;
import com.themanly.common.error.DomainRuleException;
import com.themanly.common.error.ResourceNotFoundException;
import com.themanly.measurement.MeasurementRepository;
import com.themanly.order.BespokeOrderRepository;
import com.themanly.order.OrderStatus;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 정보 (마이페이지) — 수정 · 비밀번호 변경 · 탈퇴. 대상은 언제나 로그인한 본인이다. */
@Service
public class MemberAccountService {

    private static final Logger log = LoggerFactory.getLogger(MemberAccountService.class);

    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SessionTerminator sessionTerminator;
    private final AppointmentRepository appointments;
    private final BespokeOrderRepository orders;
    private final MeasurementRepository measurements;

    public MemberAccountService(MemberRepository members, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                                SessionTerminator sessionTerminator, AppointmentRepository appointments,
                                BespokeOrderRepository orders, MeasurementRepository measurements) {
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.sessionTerminator = sessionTerminator;
        this.appointments = appointments;
        this.orders = orders;
        this.measurements = measurements;
    }

    @Transactional(readOnly = true)
    public Member get(UUID memberId) {
        return members.findById(memberId).filter(Member::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("회원 없음 id=" + memberId));
    }

    @Transactional
    public Member updateProfile(UUID memberId, String name, String phone) {
        Member member = get(memberId);
        member.setName(name.trim());
        member.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        return member;
    }

    /** 현재 비밀번호가 맞아야 바꾼다. 바꾸면 지금 세션만 남기고 다른 기기는 끊는다. */
    @Transactional
    public void changePassword(UUID memberId, String currentPassword, String newPassword, String keepSessionId) {
        Member member = get(memberId);
        if (!passwordEncoder.matches(currentPassword, member.getPasswordHash())) {
            throw DomainRuleException.badRequest("WRONG_PASSWORD", "현재 비밀번호가 올바르지 않습니다.");
        }
        passwordPolicy.validate(newPassword, member.getEmail());
        member.changePassword(passwordEncoder.encode(newPassword));
        sessionTerminator.terminateOthers(member.getEmail(), keepSessionId);
        log.info("비밀번호 변경 memberId={}", memberId);
    }

    /**
     * 탈퇴. 관리자는 못 한다(권한을 먼저 내린다). 진행 중 예약 · 맞춤 주문이 있으면 못 한다.
     * 세션을 끊고 익명화하고, 치수 기록은 지운다 — 몸 치수는 남겨 둘 이유가 없는 개인정보다.
     */
    @Transactional
    public void withdraw(UUID memberId, String password) {
        Member member = get(memberId);
        if (member.getRole().isAdmin()) {
            throw new DomainRuleException("ADMIN_CANNOT_WITHDRAW", "관리자 계정은 탈퇴할 수 없습니다. 먼저 관리자 권한을 해제해 주세요.");
        }
        if (!passwordEncoder.matches(password, member.getPasswordHash())) {
            throw DomainRuleException.badRequest("WRONG_PASSWORD", "비밀번호가 올바르지 않습니다.");
        }
        if (appointments.existsByMemberIdAndStatusIn(memberId, AppointmentStatus.LIVE)) {
            throw new DomainRuleException("APPOINTMENTS_IN_PROGRESS", "진행 중인 예약이 있어 탈퇴할 수 없습니다. 예약을 취소하거나 끝난 뒤 다시 시도해 주세요.",
                    HttpStatus.CONFLICT);
        }
        if (orders.existsByMemberIdAndStatus(memberId, OrderStatus.IN_PROGRESS)) {
            throw new DomainRuleException("ORDERS_IN_PROGRESS", "진행 중인 맞춤 주문이 있어 탈퇴할 수 없습니다. 매장으로 연락해 주세요.");
        }
        sessionTerminator.terminateAll(member.getEmail());
        measurements.deleteByMemberId(memberId);
        member.withdraw(Instant.now());
        log.info("회원 탈퇴 memberId={}", memberId);
    }
}
