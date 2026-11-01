package com.poomitda.domain.post.entity;

import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.post.entity.constant.ImageUploadStatus;
import com.poomitda.global.entity.BaseUpdatedTimeEntity;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "image_uploads",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_image_uploads_image_key",
            columnNames = "image_key"
        ),
        @UniqueConstraint(
            name = "uk_image_uploads_image_url",
            columnNames = "image_url"
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageUpload extends BaseUpdatedTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "image_upload_id", nullable = false)
  private Long imageUploadId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false, updatable = false)
  private Member uploader;

  @Column(name = "image_key", length = 255, nullable = false)
  private String imageKey;

  @Column(name = "image_url", length = 255, nullable = false)
  private String imageUrl;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "status", length = 20, nullable = false)
  private ImageUploadStatus status;

  public ImageUpload(
      Member uploader,
      String imageKey,
      String imageUrl
  ) {
    this.uploader = uploader;
    this.imageKey = imageKey;
    this.imageUrl = imageUrl;
    this.status = ImageUploadStatus.PENDING;
  }

  public void attachToPost() {
    // 현재 상태가 PENDING이 아닐 경우
    if (this.status != ImageUploadStatus.PENDING) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    // 현재 상태가 PENDING일 경우
    this.status = ImageUploadStatus.ATTACHED;
  }
}
