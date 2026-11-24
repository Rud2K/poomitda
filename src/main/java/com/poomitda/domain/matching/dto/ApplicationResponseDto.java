package com.poomitda.domain.matching.dto;

import com.poomitda.domain.matching.entity.constant.*;
import com.poomitda.domain.post.entity.constant.*;
import com.poomitda.global.constant.Species;
import java.time.OffsetDateTime;
import java.util.List;

public final class ApplicationResponseDto {

  private ApplicationResponseDto() {}

  public record CreateApplicationResponse(
      Long applicationId,
      Long postId,
      ApplicationStatus status,
      OffsetDateTime createdAt
  ) {}

  public record ApplicationCancelResponse(
      Long applicationId,
      ApplicationStatus status,
      OffsetDateTime updatedAt
  ) {}

  public record ApplicationReviewResponse(
      Long applicationId,
      ApplicationStatus status,
      OffsetDateTime updatedAt
  ) {}

  public record ApplicationRejectResponse(
      Long applicationId,
      ApplicationStatus status,
      OffsetDateTime updatedAt,
      ApplicationRejectionReason rejectionReason,
      OffsetDateTime rejectedAt
  ) {}

  public record ApplicationApproveResponse(
      Long applicationId,
      ApplicationStatus status,
      Long postId,
      PostStatus postStatus,
      PostClosedReason closedReason,
      OffsetDateTime updatedAt,
      OffsetDateTime approvedAt,
      OffsetDateTime closedAt,
      OffsetDateTime completedAt,
      ApplicationCompletionMethod completionMethod
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

  public record ApplicationListPostResponse(
      Long postId,
      String title,
      NoticeType noticeType,
      Species species,
      PostStatus status,
      String representativeImageUrl
  ) {}

  public record MyApplicationListItemResponse(
      Long applicationId,
      ApplicationStatus status,
      ApplicationListPostResponse post,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime approvedAt,
      OffsetDateTime completedAt,
      ApplicationCompletionMethod completionMethod,
      ApplicationRejectionReason rejectionReason,
      OffsetDateTime rejectedAt
  ) {}

  public record MyApplicationListResponse(
      List<MyApplicationListItemResponse> applications,
      String nextCursor,
      boolean hasNext
  ) {}

  public record PostApplicationListItemResponse(
      Long applicationId,
      ApplicationStatus status,
      ApplicationApplicantResponse applicant,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime approvedAt,
      OffsetDateTime completedAt,
      ApplicationCompletionMethod completionMethod,
      ApplicationRejectionReason rejectionReason,
      OffsetDateTime rejectedAt
  ) {}

  public record PostApplicationListResponse(
      List<PostApplicationListItemResponse> applications,
      String nextCursor,
      boolean hasNext
  ) {}
}
