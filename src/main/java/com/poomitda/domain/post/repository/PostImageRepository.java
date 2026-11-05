package com.poomitda.domain.post.repository;

import com.poomitda.domain.post.entity.PostImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

  /**
   * 공고의 이미지 목록을 표시 순서 오름차순으로 조회한다.
   *
   * @param postId 이미지를 조회할 공고 ID
   * @return 표시 순서로 정렬된 {@link PostImage}의 {@link List}, 이미지가 없으면 빈 {@link List}
   */
  List<PostImage> findAllByPost_PostIdOrderByDisplayOrderAsc(Long postId);
}
