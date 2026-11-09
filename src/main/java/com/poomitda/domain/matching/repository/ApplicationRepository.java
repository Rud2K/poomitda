package com.poomitda.domain.matching.repository;

import com.poomitda.domain.matching.entity.Application;
import com.poomitda.domain.matching.entity.constant.ApplicationStatus;
import java.util.Collection;
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

  /**
   * 공고에 대한 신청 이력이 상태와 관계없이 존재하는지 확인한다.
   *
   * @param postId 신청 이력을 확인할 공고 ID
   * @return 신청 이력이 있으면 true, 없으면 false
   */
  boolean existsByPost_PostId(Long postId);

  /**
   * 공고에 지정한 상태 중 하나에 해당하는 신청이 존재하는지 확인한다.
   *
   * @param postId 신청 상태를 확인할 공고 ID
   * @param statuses 조회할 신청 상태 목록, 비어 있지 않아야 한다.
   * @return 지정한 상태의 신청이 있으면 true, 없으면 false
   */
  boolean existsByPost_PostIdAndStatusIn(Long postId, Collection<ApplicationStatus> statuses);
}
