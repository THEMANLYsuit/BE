package com.themanly.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** 새 링크를 만들 때 그 회원의 옛 링크는 전부 무효로 — 살아 있는 링크는 하나만. */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.memberId = :memberId")
    int deleteByMemberId(UUID memberId);
}
