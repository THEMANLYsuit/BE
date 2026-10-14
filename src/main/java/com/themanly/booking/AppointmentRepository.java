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
}
