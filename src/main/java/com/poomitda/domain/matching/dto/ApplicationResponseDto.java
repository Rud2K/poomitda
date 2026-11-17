package com.poomitda.domain.matching.dto;

import com.poomitda.domain.matching.entity.constant.*;
import com.poomitda.domain.post.entity.constant.*;
import com.poomitda.global.constant.Species;
import java.time.OffsetDateTime;

public final class ApplicationResponseDto {

  private ApplicationResponseDto() {}

  public record CreateApplicationResponse(
      Long applicationId,
      Long postId,
      ApplicationStatus status,
      OffsetDateTime createdAt
  ) {}

  public record ApplicationApplicantResponse(
      Long memberId,
      String nickname
  ) {}

  public record ApplicationPostResponse(
      Long postId,
      String title,
      NoticeType noticeType,
      Species species,
      PostStatus status
  ) {}

  public record ApplicationDetailResponse(
      Long applicationId,
      ApplicationStatus status,
      ApplicationApplicantResponse applicant,
      ApplicationPostResponse post,
      String occupation,
      HousingType housingType,
      HousingOwnership housingOwnership,
      Boolean isLandlordAgreed,
      boolean hasYardOrBalcony,
      int timeAwayPerDay,
      int familyCount,
      String familyAges,
      boolean hasCohabitantAgreed,
      boolean hasAllergy,
      String currentPets,
      String pastExperienceDetail,
      boolean isFinanciallyReady,
      boolean pledgeAgreed,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime approvedAt,
      OffsetDateTime completedAt,
      ApplicationCompletionMethod completionMethod,
      ApplicationRejectionReason rejectionReason,
      OffsetDateTime rejectedAt
  ) {}
}
