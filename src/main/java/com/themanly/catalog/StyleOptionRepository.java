package com.themanly.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StyleOptionRepository extends JpaRepository<StyleOption, UUID> {

    List<StyleOption> findByEnabledTrueOrderByCategoryAscSortOrderAsc();

    List<StyleOption> findAllByOrderByCategoryAscSortOrderAsc();

    boolean existsByCategoryAndCode(StyleOption.Category category, String code);

    Optional<StyleOption> findByCategoryAndDefaultChoiceTrue(StyleOption.Category category);

    @Query("SELECT COALESCE(MAX(o.sortOrder), 0) FROM StyleOption o WHERE o.category = :category")
    int maxSortOrder(StyleOption.Category category);
}
