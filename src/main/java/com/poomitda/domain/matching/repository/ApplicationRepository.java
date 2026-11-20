package com.poomitda.domain.matching.repository;

import com.poomitda.domain.matching.entity.Application;
import com.poomitda.domain.matching.entity.constant.ApplicationStatus;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

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

  /**
   * 신청을 신청자, 공고 및 공고 작성자 정보와 함께 조회한다.
   * 공고의 삭제 여부와 신청 상태에 관계없이 조회한다.
   *
   * @param applicationId 조회할 신청 ID
   * @return 신청이 존재하면 {@link Application}을 담은 {@link Optional}, 없으면 빈 {@link Optional}
   */
  @EntityGraph(attributePaths = {"applicant", "post", "post.author"})
  Optional<Application> findByApplicationId(Long applicationId);

  /**
   * 회원이 작성한 신청을 상태와 커서 조건에 따라 최신순으로 조회한다.
   * 연결된 공고의 삭제 여부와 관계없이 조회한다.
   *
   * @param memberId 현재 로그인한 신청자 회원 ID
   * @param status 조회할 신청 상태, null이면 전체 상태
   * @param cursorCreatedAt 커서의 UTC 생성 시각, 첫 페이지이면 null
   * @param cursorApplicationId 커서의 신청 ID, 첫 페이지이면 null
   * @param pageable 페이지 번호는 0, 조회 개수는 요청 size + 1로 전달
   * @return 공고 정보를 포함한 신청 목록, 생성 시각과 신청 ID 내림차순
   */
  @EntityGraph(attributePaths = "post")
  @Query("""
    SELECT a
    FROM Application a
    WHERE a.applicant.memberId = :memberId
      AND (:status IS NULL OR a.status = :status)
      AND (
        :cursorCreatedAt IS NULL
        OR a.createdAt < :cursorCreatedAt
        OR (
          a.createdAt = :cursorCreatedAt
          AND a.applicationId < :cursorApplicationId
        )
      )
    ORDER BY a.createdAt DESC, a.applicationId DESC
    """)
  List<Application> findMyApplications(
      @Param("memberId") Long memberId,
      @Param("status") ApplicationStatus status,
      @Param("cursorCreatedAt")LocalDateTime cursorCreatedAt,
      @Param("cursorApplicationId") Long cursorApplicationId,
      Pageable pageable
  );

  /**
   * 공고에 접수된 신청을 상태와 커서 조건에 따라 최신순으로 조회한다.
   * 연결된 공고의 삭제 여부와 관계 없이 조회한다.
   *
   * @param postId 작성자 권한 검증을 통과한 조회 대상 공고 ID
   * @param status 조회할 신청 상태, null이면 전체 상태
   * @param cursorCreatedAt 커서의 UTC 생성 시각, 첫 페이지이면 null
   * @param cursorApplicationId 커서의 신청 ID, 첫 페이지이면 null
   * @param pageable 페이지 번호는 0, 조회 개수는 요청 size + 1로 전달
   * @return 신청자 정보를 포함한 신청 목록, 생성 시각과 신청 ID 내림차순
   */
  @EntityGraph(attributePaths = "applicant")
  @Query("""
    SELECT a
    FROM Application a
    WHERE a.post.postId = :postId
      AND (:status IS NULL OR a.status = :status)
      AND (
        :cursorCreatedAt IS NULL
        OR a.createdAt < :cursorCreatedAt
        OR (
          a.createdAt = :cursorCreatedAt
          AND a.applicationId < :cursorApplicationId
        )
      )
    ORDER BY a.createdAt DESC, a.applicationId DESC
    """)
  List<Application> findPostApplications(
      @Param("postId") Long postId,
      @Param("status") ApplicationStatus status,
      @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
      @Param("cursorApplicationId") Long cursorApplicationId,
      Pageable pageable
  );
}
