package com.themanly.inquiry;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, UUID> {

    List<Inquiry> findByMemberIdOrderByCreatedAtDesc(UUID memberId);

    Page<Inquiry> findByStatusInOrderByCreatedAtDesc(Collection<Inquiry.Status> statuses, Pageable pageable);

    long countByStatus(Inquiry.Status status);
}