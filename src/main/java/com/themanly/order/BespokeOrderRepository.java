package com.themanly.order;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BespokeOrderRepository extends JpaRepository<BespokeOrder, UUID> {
    Optional<BespokeOrder> findByOrderNumber(String orderNumber);
    boolean existsByOrderNumber(String orderNumber);
    List<BespokeOrder> findByMemberIdOrderByCreatedAtDesc(UUID memberId);
    boolean existsByMemberIdAndStatus(UUID memberId, OrderStatus status);
    @Query("""
            SELECT o FROM BespokeOrder o
            WHERE o.status IN :statuses
              AND o.stage IN :stages
              AND (lower(o.orderNumber) LIKE :q ESCAPE '!'
                   OR lower(o.title) LIKE :q ESCAPE '!'
                   OR o.memberId IN (SELECT m.id FROM Member m
                                     WHERE lower(m.name) LIKE :q ESCAPE '!' OR m.email LIKE :q ESCAPE '!'))
            """)
    Page<BespokeOrder> search(@Param("q") String q, @Param("statuses") Collection<OrderStatus> statuses, @Param("stages") Collection<OrderStage> stages, Pageable pageable);}