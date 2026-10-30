package com.themanly.rental;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RentalRepository extends JpaRepository<Rental, UUID> {
    List<Rental> findByMemberIdOrderByPickupDateDesc(UUID memberId);
    long countByMemberIdAndStatusIn(UUID memberId, Collection<RentalStatus> statuses);
    boolean existsByMemberIdAndStatusIn(UUID memberId, Collection<RentalStatus> statuses);
    List<Rental> findByPickupDateBetweenAndStatusInOrderByPickupDateAscCreatedAtAsc(LocalDate from, LocalDate to, Collection<RentalStatus> statuses);
}