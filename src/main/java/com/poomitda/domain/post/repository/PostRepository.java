package com.poomitda.domain.post.repository;

import com.poomitda.domain.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

  /**
   * 회원이 작성한 삭제되지 않은 공고 수를 조회한다.
   *
   * @param memberId 공고 작성자의 회원 ID
   * @return 모집 상태와 관계없이 집계한 공고 수, 없으면 0
   */
  long countByAuthor_MemberIdAndDeletedAtIsNull(Long memberId);
}
