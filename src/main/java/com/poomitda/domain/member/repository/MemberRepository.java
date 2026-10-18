package com.poomitda.domain.member.repository;

import com.poomitda.domain.member.entity.Member;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

  /**
   * 이메일 존재 여부 조회
   *
   * @param email 검증 대상 email
   * @return 중복되는 email이 존재할 경우 true, 존재하지 않을 경우 false
   */
  boolean existsByEmail(String email);

  /**
   * 닉네임 존재 여부 조회
   *
   * @param nickname 검증 대상 nickname
   * @return 중복되는 nickname이 존재할 경우 true, 존재하지 않을 경우 false
   */
  boolean existsByNickname(String nickname);

  /**
   * 이메일로 회원을 조회하고 비관적 쓰기 락을 획득한다.
   *
   * @param email 조회할 회원의 이메일
   * @return 조회된 회원, 없으면 비어있는 {@link Optional}
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT m FROM Member m WHERE m.email = :email")
  Optional<Member> findByEmailForUpdate(@Param("email") String email);

  /**
   * 회원 ID로 회원을 조회하고 비관적 쓰기 락을 획득한다.
   *
   * @param memberId 조회할 회원 ID
   * @return 조회된 회원, 없으면 비어있는 {@link Optional}
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT m FROM Member m WHERE m.memberId = :memberId")
  Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);
}
