package com.poomitda.global.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.*;

@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseDeletedTimeEntity extends BaseUpdatedTimeEntity {

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  // 최초 삭제 시각을 기록하고, 반복 호출 시 기존 값을 유지
  protected void markDeleted(LocalDateTime deletedAt) {
    Objects.requireNonNull(deletedAt,
        () -> "삭제 시각 기록 실패: "
            + getClass().getSimpleName()
            + ".markDeleted()에 null이 전달되었습니다."
    );

    if (this.deletedAt != null) {
      return;
    }

    this.deletedAt = deletedAt;
  }
}
