package com.poomitda.domain.auth.repository;

import com.poomitda.domain.auth.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  /**
   * 회원 ID로 저장된 Refresh Token을 조회한다.
   *
   * @param memberId 조회할 회원 ID
   * @return 저장된 토큰 정보, 없으면 비어있는 Optional
   */
  Optional<RefreshToken> findByMember_MemberId(Long memberId);
}
