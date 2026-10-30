package com.themanly.booking;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    /** 그 시각에 살아 있는 예약이 있는가. 마지막 방어는 DB 부분 유니크 인덱스(V4)다. */
    boolean existsByStartsAtAndStatusIn(Instant startsAt, Collection<AppointmentStatus> statuses);

    List<Appointment> findByStartsAtBetweenAndStatusInOrderByStartsAtAsc(Instant from, Instant to,
                                                                          Collection<AppointmentStatus> statuses);

    List<Appointment> findByMemberIdOrderByStartsAtDesc(UUID memberId);

    long countByMemberIdAndStatusIn(UUID memberId, Collection<AppointmentStatus> statuses);

    boolean existsByMemberIdAndStatusIn(UUID memberId, Collection<AppointmentStatus> statuses);

    /** 한 주문에 묶인 예약 — 관리자 주문 상세의 "가봉 일정". */
    List<Appointment> findByOrderIdOrderByStartsAtAsc(UUID orderId);

    /** 지금부터 — 관리자 회원 상세 · 대시보드의 "다가오는 예약". */
    List<Appointment> findByMemberIdAndStartsAtAfterAndStatusInOrderByStartsAtAsc(UUID memberId, Instant after,
                                                                                   Collection<AppointmentStatus> statuses);

    long countByStartsAtBetweenAndStatusIn(Instant from, Instant to, Collection<AppointmentStatus> statuses);

    long countByStatus(AppointmentStatus status);

    /** 전날 알림 대상 — 그날 [from, to) 에 시작하는, 그 상태의, 아직 알리지 않은 예약(V12, BookingReminder). */
    List<Appointment> findByStartsAtGreaterThanEqualAndStartsAtLessThanAndStatusAndRemindedAtIsNullOrderByStartsAtAsc(
            Instant from, Instant to, AppointmentStatus status);
}
