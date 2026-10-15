package com.themanly.measurement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeasurementRepository extends JpaRepository<Measurement, UUID> {

    List<Measurement> findByMemberIdOrderByMeasuredOnDescCreatedAtDesc(UUID memberId);

    Optional<Measurement> findFirstByMemberIdOrderByMeasuredOnDescCreatedAtDesc(UUID memberId);

    void deleteByMemberId(UUID memberId);
}