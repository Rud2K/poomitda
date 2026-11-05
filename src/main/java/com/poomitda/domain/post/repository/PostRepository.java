package com.poomitda.domain.post.repository;

import com.poomitda.domain.post.entity.Post;
import com.poomitda.domain.post.entity.constant.PostStatus;
import java.util.*;
import org.springframework.data.jpa.repository.*;

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
}
