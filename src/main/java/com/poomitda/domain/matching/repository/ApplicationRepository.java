package com.poomitda.domain.matching.repository;

import com.poomitda.domain.matching.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

  /**
   * 공고에 대한 회원의 신청이 상태와 관계없이 존재하는지 확인한다.
   *
   * @param postId 신청 여부를 확인할 공고 ID
   * @param applicantId 신청자의 회원 ID
   * @return 신청이 존재하면 true, 없으면 false
   */
  boolean existsByPost_PostIdAndApplicant_MemberId(Long postId, Long applicantId);
}
