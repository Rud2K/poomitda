package com.poomitda.domain.post.repository;

import com.poomitda.domain.post.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

  /**
   * 회원이 해당 공고를 북마크했는지 확인한다.
   *
   * @param memberId 북마크 여부를 확인할 회원 ID
   * @param postId 조회할 공고 ID
   * @return 북마크가 존재하면 true, 없으면 false
   */
  boolean existsByMember_MemberIdAndPost_PostId(Long memberId, Long postId);
}
