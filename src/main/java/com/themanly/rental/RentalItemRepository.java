package com.themanly.rental;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RentalItemRepository extends JpaRepository<RentalItem, UUID> {
    List<RentalItem> findByStatusInOrderBySortOrderAscNameAsc(Collection<RentalItem.Status> statuses);
    List<RentalItem> findAllByOrderBySortOrderAscNameAsc();
    @Query("SELECT COALESCE(MAX(r.sortOrder), 0) FROM RentalItem r")
    int maxSortOrder();
}
