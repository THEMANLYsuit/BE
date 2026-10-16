package com.themanly.admin;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminAccessLogRepository extends JpaRepository<AdminAccessLog, Long> {
    @Query(value = """
            SELECT * FROM admin_access_log
            WHERE (CAST(:actorId AS uuid) IS NULL OR actor_id = CAST(:actorId AS uuid))
              AND (CAST(:from AS timestamptz) IS NULL OR created_at >= CAST(:from AS timestamptz))
              AND (CAST(:to AS timestamptz) IS NULL OR created_at < CAST(:to AS timestamptz))
              AND (CAST(:path AS text) IS NULL OR path LIKE CAST(:path AS text))
            ORDER BY id DESC
            """, countQuery = """
            SELECT count(*) FROM admin_access_log
            WHERE (CAST(:actorId AS uuid) IS NULL OR actor_id = CAST(:actorId AS uuid))
              AND (CAST(:from AS timestamptz) IS NULL OR created_at >= CAST(:from AS timestamptz))
              AND (CAST(:to AS timestamptz) IS NULL OR created_at < CAST(:to AS timestamptz))
              AND (CAST(:path AS text) IS NULL OR path LIKE CAST(:path AS text))
            """, nativeQuery = true)
    Page<AdminAccessLog> search(@Param("actorId") UUID actorId, @Param("from") Instant from, @Param("to") Instant to,
                                @Param("path") String path, Pageable pageable);
}