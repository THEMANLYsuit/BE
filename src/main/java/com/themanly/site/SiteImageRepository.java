package com.themanly.site;

import jakarta.persistence.Entity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SiteImageRepository extends JpaRepository<SiteImage, SiteImage.Slot> {
    @EntityGraph(attributePaths = "media")
    List<SiteImage> findAllByOrderBySlotAsc();
}
