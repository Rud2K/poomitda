package com.poomitda.domain.post.dto;

import com.poomitda.domain.post.entity.constant.*;
import com.poomitda.global.constant.*;
import java.time.*;
import java.util.List;

public final class PostResponseDto {

  private PostResponseDto() {}

  public record CreatePostResponse(
      Long postId
  ) {}

  public record ImageUploadResponse(
      String imageKey,
      String imageUrl
  ) {}

  public record PostWriterResponse(
      Long memberId,
      String nickname,
      Double averageRating,
      int reviewCount
  ) {}

  public record PostImageResponse(
      Long postImageId,
      String imageUrl,
      boolean isRepresentative,
      int displayOrder
  ) {}

  public record PostDetailResponse(
      Long postId,
      boolean isWriter,
      boolean isBookmarked,
      PostWriterResponse writer,
      String title,
      String content,
      NoticeType noticeType,
      Species species,
      String breed,
      LocalDate estimatedBirthDate,
      Gender gender,
      boolean isNeutered,
      String fosterPeriod,
      String foundLocation,
      Region region,
      String district,
      PostStatus status,
      PostClosedReason closedReason,
      OffsetDateTime recruitmentDeadline,
      int bookmarkCount,
      List<PostImageResponse> images,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime closedAt,
      boolean canApply
  ) {}
}
