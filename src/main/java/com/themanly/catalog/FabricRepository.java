package com.themanly.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FabricRepository extends JpaRepository<Fabric, UUID> {

    /** 손님 목록 — 상태를 호출하는 쪽이 준다(PUBLISHED · SOLD_OUT). 관리자가 놓은 순서, 같으면 이름순. */
    List<Fabric> findByStatusInOrderBySortOrderAscNameAsc(Collection<Fabric.Status> statuses);

    List<Fabric> findAllByOrderBySortOrderAscNameAsc();

    Optional<Fabric> findByCode(String code);

    boolean existsByCode(String code);

    @Query("SELECT COALESCE(MAX(f.sortOrder), 0) FROM Fabric f")
    int maxSortOrder();

    List<Fabric> findByIdIn(Collection<UUID> ids);
}
