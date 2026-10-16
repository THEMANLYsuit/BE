package com.themanly.film;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FilmRepository extends JpaRepository<Film, UUID> {
    Optional<Film> findFirstByActiveTrue();
    List<Film> findTop10ByOrderByCreatedAtDesc();
}