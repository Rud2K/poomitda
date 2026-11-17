package com.poomitda.domain.matching.dto;

import com.poomitda.domain.matching.entity.constant.ApplicationStatus;
import java.time.OffsetDateTime;

public final class ApplicationResponseDto {

  private ApplicationResponseDto() {}

  public record CreateApplicationResponse(
      Long applicationId,
      Long postId,
      ApplicationStatus status,
      OffsetDateTime createdAt
  ) {}
}
