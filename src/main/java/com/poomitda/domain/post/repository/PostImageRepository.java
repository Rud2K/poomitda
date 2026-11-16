package com.poomitda.domain.post.repository;

import com.poomitda.domain.post.entity.PostImage;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

  /**
   * 공고의 이미지 목록을 표시 순서 오름차순으로 조회한다.
   *
   * @param postId 이미지를 조회할 공고 ID
   * @return 표시 순서로 정렬된 {@link PostImage}의 {@link List}, 이미지가 없으면 빈 {@link List}
   */
  List<PostImage> findAllByPost_PostIdOrderByDisplayOrderAsc(Long postId);

  interface RepresentativeImageView {
    Long getPostId();
    String getImageUrl();
  }

  /**
   * 지정한 공고들의 대표 이미지 URL을 한 번에 조회한다.
   *
   * @param postIds 대표 이미지를 조회할 공고 ID 목록. 비어있지 않아야 함
   * @return 공고 ID와 대표 이미지 URL을 담은 {@link RepresentativeImageView} 목록
   */
  @Query("""
      SELECT pi.post.postId AS postId,
             pi.imageUrl AS imageUrl
      FROM PostImage pi
      WHERE pi.post.postId IN :postIds
        AND pi.isRepresentative = true
      """)
  List<RepresentativeImageView> findRepresentativeImagesByPostIds(
      @Param("postIds") Collection<Long> postIds
  );
}
