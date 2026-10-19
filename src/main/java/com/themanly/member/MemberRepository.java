package com.themanly.member;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 회원 조회. 이메일로 찾을 때는 반드시 {@link Member#normalizeEmail} 를 거친 값을 넘긴다. */
public interface MemberRepository extends JpaRepository<Member, UUID> {

    Optional<Member> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);

    Optional<Member> findByLoginId(String loginId);

    /**
     * 관리자 회원 검색. 이메일 · 이름 · 전화번호(하이픈 무시)에서 찾는다.
     * {@code :q} 는 이미 {@code %…%} 로 감싸고 이스케이프한 소문자 패턴, {@code :digits} 는 숫자만 모은 패턴이다
     * ({@code AdminMemberService}).
     */
    @Query("""
            SELECT m FROM Member m
            WHERE m.status IN :statuses
              AND (m.email LIKE :q ESCAPE '!'
                   OR lower(m.name) LIKE :q ESCAPE '!'
                   OR replace(coalesce(m.phone, ''), '-', '') LIKE :digits)
            """)
    Page<Member> search(@Param("q") String q, @Param("digits") String digits,
                        @Param("statuses") Collection<MemberStatus> statuses, Pageable pageable);

    /**
     * 아이디 찾기 ({@code FindEmailService}) — 이용 중인 일반 회원 중 이름(소문자 · 앞뒤 공백 없이)과
     * 전화번호(숫자만, 하이픈 무시)가 같은 사람. 먼저 가입한 계정부터.
     */
    @Query("""
            SELECT m FROM Member m
            WHERE m.status = com.themanly.member.MemberStatus.ACTIVE
              AND m.role = com.themanly.member.MemberRole.MEMBER
              AND lower(trim(m.name)) = :name
              AND replace(coalesce(m.phone, ''), '-', '') = :digits
            ORDER BY m.createdAt ASC
            """)
    List<Member> findForEmailLookup(@Param("name") String name, @Param("digits") String digits, Pageable pageable);
}
