package com.poomitda.domain.member.repository;

import com.poomitda.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.ResponseEntity;

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
}
