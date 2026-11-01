package com.poomitda.domain.post.repository;

import com.poomitda.domain.post.entity.ImageUpload;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ImageUploadRepository extends JpaRepository<ImageUpload, Long> {

  /**
   * 이미지 URL 목록으로 업로드 이력을 조회하고 비관적 쓰기 락을 획득한다.
   *
   * @param imageUrls 조회할 이미지 URL 목록
   * @return 업로드 이력 ID 오름차순의 조회 결과, 없으면 빈 목록
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT iu FROM ImageUpload iu WHERE iu.imageUrl IN :imageUrls ORDER BY iu.imageUploadId ASC")
  List<ImageUpload> findAllByImageUrlInForUpdate(
      @Param("imageUrls") List<String> imageUrls
  );
}
