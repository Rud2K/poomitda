package com.poomitda.domain.post.repository;

import com.poomitda.domain.post.entity.Post;
import com.poomitda.domain.post.entity.constant.PostStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

  /**
   * 회원이 작성한 삭제되지 않은 공고 수를 조회한다.
   *
   * @param memberId 공고 작성자의 회원 ID
   * @return 모집 상태와 관계없이 집계한 공고 수, 없으면 0
   */
  long countByAuthor_MemberIdAndDeletedAtIsNull(Long memberId);

  /**
   * 회원이 작성한 공고 중 지정한 상태의 공고가 있는지 삭제 여부와 관계없이 조회한다.
   *
   * @param memberId 공고 작성자의 회원 ID
   * @param statuses 조회할 공고 상태 목록
   * @return 조건에 맞는 공고가 있으면 true, 없으면 false
   */
  boolean existsByAuthor_MemberIdAndStatusIn(
      Long memberId,
      Collection<PostStatus> statuses
  );

  /**
   * 삭제되지 않은 공고를 작성자 정보와 함께 조회한다.
   *
   * @param postId 조회할 공고 ID
   * @return 공고가 존재하면 {@link Post}를 담은 {@link Optional}, 없거나 삭제되었으면 빈 {@link Optional}
   */
  @EntityGraph(attributePaths = "author")
  Optional<Post> findByPostIdAndDeletedAtIsNull(Long postId);

  /**
   * 삭제되지 않은 공고를 조회하고 비관적 쓰기 잠금을 획득한다.
   *
   * @param postId 상태를 변경할 공고 ID
   * @return 공고가 존재하면 {@link Post}를 담은 {@link Optional}, 없거나 삭제되었으면 빈 {@link Optional}
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Post p WHERE p.postId = :postId AND p.deletedAt IS NULL")
  Optional<Post> findByIdForUpdate(@Param("postId") Long postId);

  /**
   * 기준 시각에 마감된 미종료 공고의 ID를 마지막 조회 ID 이후부터 오름차순으로 조회한다.
   *
   * @param statuses 조회할 공고 상태 목록. 비어있지 않아야 한다.
   * @param now 마감 여부를 판단할 UTC 기준 시각
   * @param lastPostId 직전 묶음의 마지막 공고 ID. 최초 조회에서는 0
   * @param pageable 조회 개수 제한. 페이지 번호는 0으로 전달
   * @return 처리 대상 공고 ID를 담은 {@link List}, 대상이 없으면 빈 목록
   */
  @Query("""
      SELECT p.postId
      FROM Post p
      WHERE p.deletedAt IS NULL
        AND p.status IN :statuses
        AND p.recruitmentDeadline IS NOT NULL
        AND p.recruitmentDeadline <= :now
        AND p.postId > :lastPostId
      ORDER BY p.postId ASC
  """)
  List<Long> findDeadlineTargetIds(
      @Param("statuses") Collection<PostStatus> statuses,
      @Param("now") LocalDateTime now,
      @Param("lastPostId") Long lastPostId,
      Pageable pageable
  );
}
