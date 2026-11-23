package com.poomitda.domain.matching.service;

import com.poomitda.domain.matching.dto.ApplicationRequestDto.CreateApplicationRequest;
import com.poomitda.domain.matching.dto.ApplicationResponseDto;
import com.poomitda.domain.matching.dto.ApplicationResponseDto.*;
import com.poomitda.domain.matching.entity.Application;
import com.poomitda.domain.matching.entity.constant.ApplicationStatus;
import com.poomitda.domain.matching.exception.ApplicationErrorCode;
import com.poomitda.domain.matching.repository.ApplicationRepository;
import com.poomitda.domain.matching.validation.ApplicationCreateValidator;
import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.entity.constant.*;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.domain.post.entity.Post;
import com.poomitda.domain.post.entity.constant.*;
import com.poomitda.domain.post.exception.PostErrorCode;
import com.poomitda.domain.post.repository.PostRepository;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationService {

  private final MemberRepository memberRepository;
  private final PostRepository postRepository;
  private final ApplicationRepository applicationRepository;
  private final ApplicationCreateValidator applicationCreateValidator;

  /**
   * 신청자와 공고를 잠금 조회하고 신청 조건을 검증한 뒤 신청서를 생성한다.
   *
   * @param memberId 현재 로그인한 신청자 회원 ID
   * @param request 기본 입력값 검증을 통과한 신청 생성 요청
   * @return 생성된 신청 정보를 담은 {@link CreateApplicationResponse}
   * @throws IllegalArgumentException 요청이 null인 경우
   * @throws BusinessException 신청자, 공고 또는 신청 조건이 유효하지 않은 경우
   */
  @Transactional
  public ApplicationResponseDto.CreateApplicationResponse createApplication(
      Long memberId,
      CreateApplicationRequest request
  ) {
    // 요청 객체 누락 확인
    if (request == null) {
      throw new IllegalArgumentException(
          "신청 생성 요청은 null일 수 없습니다."
      );
    }

    // 주거 소유 형태에 따른 임대인 등의 입력 여부 검증
    applicationCreateValidator.validate(request);

    // 신청자를 잠금 조회하고 상태와 역할 검증
    Member applicant = getApplicantForUpdate(memberId);

    // 삭제되지 않은 공고를 잠금 조회
    Post post = postRepository.findByIdForUpdate(request.postId())
        .orElseThrow(
            () -> new BusinessException(PostErrorCode.POST_NOT_FOUND)
        );

    // 공고가 모집 중인지 확인
    if (post.getStatus() != PostStatus.RECRUITING) {
      throw new BusinessException(ApplicationErrorCode.POST_NOT_RECRUITING);
    }

    // 본인 공고인지 확인
    if (post.getAuthor().getMemberId().equals(applicant.getMemberId())) {
      throw new BusinessException(ApplicationErrorCode.CANNOT_APPLY_OWN_POST);
    }

    // 상태와 관계없이 기존 신청 이력 확인
    boolean hasApplied = applicationRepository.existsByPost_PostIdAndApplicant_MemberId(
        post.getPostId(),
        applicant.getMemberId()
    );

    if (hasApplied) {
      throw new BusinessException(ApplicationErrorCode.APPLICATION_ALREADY_EXISTS);
    }

    // 잠금을 획득한 상태에서 현재 시각으로 접수 마감 여부 확인
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

    if (post.isRecruitmentDeadlineReached(now)) {
      throw new BusinessException(ApplicationErrorCode.APPLICATION_DEADLINE_PASSED);
    }

    // 신청서 엔티티 생성
    Application application = new Application(
        post,
        applicant,
        request.occupation(),
        request.housingType(),
        request.housingOwnership(),
        request.isLandlordAgreed(),
        request.hasYardOrBalcony(),
        request.timeAwayPerDay(),
        request.familyCount(),
        request.familyAges(),
        request.hasCohabitantAgreed(),
        request.hasAllergy(),
        request.currentPets(),
        request.pastExperienceDetail(),
        request.isFinanciallyReady(),
        request.pledgeAgreed()
    );

    // 신청서 저장
    Application savedApplication = applicationRepository.save(application);

    // 저장된 생성 시각을 동일한 순간의 +09:00 시각으로 변환
    OffsetDateTime createdAt = savedApplication.getCreatedAt()
        .atOffset(ZoneOffset.UTC)
        .withOffsetSameInstant(ZoneOffset.ofHours(9));

    // CreateApplicationResponse 생성 후 반환
    return new ApplicationResponseDto.CreateApplicationResponse(
        savedApplication.getApplicationId(),
        post.getPostId(),
        savedApplication.getStatus(),
        createdAt
    );
  }

  /**
   * 신청자 본인 또는 해당 공고 작성자에게 신청 상세 정보를 반환한다.
   *
   * @param memberId 현재 로그인한 회원 ID
   * @param applicationId 조회할 신청 ID
   * @return 신청서와 관련 요약 정보를 담은 {@link ApplicationDetailResponse}
   * @throws BusinessException 인증 정보가 유효하지 않거나 조회 권한이 없거나 신청이 없는 경우
   */
  @Transactional(readOnly = true)
  public ApplicationResponseDto.ApplicationDetailResponse getApplicationDetail(
      Long memberId,
      Long applicationId
  ) {
    // 현재 요청자의 인증 정보 확인
    if (memberId == null) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED) ;
    }

    Member currentMember = memberRepository.findById(memberId)
        .orElseThrow(
            () -> new BusinessException(CommonErrorCode.UNAUTHORIZED)
        );

    if (currentMember.getStatus() != MemberStatus.ACTIVE) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }

    // 현재 요청자의 역할 확인
    MemberRole role = currentMember.getRole();

    if (role != MemberRole.ROLE_USER && role != MemberRole.ROLE_PROTECTOR) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    // 신청 ID의 유효성 확인
    if (applicationId == null || applicationId <= 0) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    // 신청과 관련 정보 조회
    Application application = applicationRepository.findByApplicationId(applicationId)
        .orElseThrow(
            () -> new BusinessException(ApplicationErrorCode.APPLICATION_NOT_FOUND)
        );

    // 조회 권한 판단에 사용할 관계 준비
    Member applicant = application.getApplicant();
    Post post = application.getPost();

    // 신청자 본인 또는 공고 작성자인지 확인
    if (!memberId.equals(applicant.getMemberId())
        && !memberId.equals(post.getAuthor().getMemberId())) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    // 신청자 요약 응답 생성
    ApplicationApplicantResponse applicantResponse = new ApplicationApplicantResponse(
        applicant.getMemberId(),
        applicant.getNickname()
    );

    // 공고 요약 응답 생성
    ApplicationPostResponse postResponse = new ApplicationPostResponse(
        post.getPostId(),
        post.getTitle(),
        post.getNoticeType(),
        post.getSpecies(),
        post.getStatus()
    );

    // 전체 신청 상세 응답 생성 후 반환
    return new ApplicationResponseDto.ApplicationDetailResponse(
        applicationId,
        application.getStatus(),
        applicantResponse,
        postResponse,
        application.getOccupation(),
        application.getHousingType(),
        application.getHousingOwnership(),
        application.getIsLandlordAgreed(),
        application.isHasYardOrBalcony(),
        application.getTimeAwayPerDay(),
        application.getFamilyCount(),
        application.getFamilyAges(),
        application.isHasCohabitantAgreed(),
        application.isHasAllergy(),
        application.getCurrentPets(),
        application.getPastExperienceDetail(),
        application.isFinanciallyReady(),
        application.isPledgeAgreed(),
        toResponseTime(application.getCreatedAt()),
        toResponseTime(application.getUpdatedAt()),
        toResponseTime(application.getApprovedAt()),
        toResponseTime(application.getCompletedAt()),
        application.getCompletionMethod(),
        application.getRejectionReason(),
        toResponseTime(application.getRejectedAt())
    );
  }

  /**
   * 본인의 접수 상태 신청을 취소하고 필요한 공고 상태 변경을 처리한다.
   *
   * @param memberId 현재 로그인한 회원 ID
   * @param applicationId 취소할 신청 ID
   * @return 취소된 신청 정보를 담은 {@link ApplicationCancelResponse}
   * @throws BusinessException 인증, 권한, 입력값 또는 신청 상태가 유효하지 않은 경우
   */
  @Transactional
  public ApplicationResponseDto.ApplicationCancelResponse cancelApplication(
      Long memberId,
      Long applicationId
  ) {
    // 로그인 회원 ID 누락 확인
    if (memberId == null) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }

    // 현재 회원을 잠금 조회하고 활성 상태와 역할 검증
    Member currentMember = getApplicantForUpdate(memberId);

    // 신청 ID 검증
    if (applicationId == null || applicationId <= 0) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    // 신청이 속한 공고 ID 조회
    Long postId = applicationRepository.findPostIdByApplicationId(applicationId)
        .orElseThrow(
            () -> new BusinessException(ApplicationErrorCode.APPLICATION_NOT_FOUND)
        );

    // 삭제된 공고도 포함하여 공고 잠금 조회
    Post post = postRepository.findByIdIncludingDeletedForUpdate(postId)
        .orElseThrow(
            () -> new BusinessException(PostErrorCode.POST_NOT_FOUND)
        );

    // 신청 잠금 조회
    Application application = applicationRepository.findByIdForUpdate(applicationId)
        .orElseThrow(
            () -> new BusinessException(ApplicationErrorCode.APPLICATION_NOT_FOUND)
        );

    // 신청자 본인인지 확인
    if (!currentMember.getMemberId().equals(application.getApplicant().getMemberId())) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    // 접수 상태의 신청을 취소
    application.cancel();

    // 신청 상태 변경과 Auditing 수정 시각을 DB에 반영
    applicationRepository.flush();

    // 취소 후 남은 신청과 접수 마감 여부에 따라 공고 상태 갱신
    updatePostStatusAfterCancellation(post);

    // 취소된 신청 정보와 한국 시각으로 변환한 수정 시각 반환
    return new ApplicationCancelResponse(
        application.getApplicationId(),
        application.getStatus(),
        toResponseTime(application.getUpdatedAt())
    );
  }

  /**
   * 공고 작성자가 접수 상태 신청의 심사를 시작한다.
   *
   * @param memberId 현재 로그인한 보호자 회원 ID
   * @param applicationId 심사를 시작할 신청 ID
   * @return 심사를 시작한 신청 정보를 담은 {@link ApplicationReviewResponse}
   * @throws BusinessException 인증, 권한, 입력값, 공고 또는 신청 상태가 유효하지 않은 경우
   */
  @Transactional
  public ApplicationResponseDto.ApplicationReviewResponse reviewApplication(
      Long memberId,
      Long applicationId
  ) {
    // 로그인 회원 ID 누락 확인
    if (memberId == null) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }

    // 현재 회원을 잠금 조회하고 활성 상태 검증
    Member currentMember = memberRepository.findByIdForUpdate(memberId)
        .orElseThrow(
            () -> new BusinessException(CommonErrorCode.UNAUTHORIZED)
        );

    if (currentMember.getStatus() != MemberStatus.ACTIVE) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }

    // 현재 회원의 보호자 역할 검증
    if (currentMember.getRole() != MemberRole.ROLE_PROTECTOR) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    // 신청 ID 검증
    if (applicationId == null || applicationId <= 0) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    // 신청이 속한 공고 ID 조회
    Long postId = applicationRepository.findPostIdByApplicationId(applicationId)
        .orElseThrow(
            () -> new BusinessException(ApplicationErrorCode.APPLICATION_NOT_FOUND)
        );

    // 삭제되지 않은 공고를 잠금 조회
    Post post = postRepository.findByIdForUpdate(postId)
        .orElseThrow(
            () -> new BusinessException(PostErrorCode.POST_NOT_FOUND)
        );

    // 신청 잠금 조회
    Application application = applicationRepository.findByIdForUpdate(applicationId)
        .orElseThrow(
            () -> new BusinessException(ApplicationErrorCode.APPLICATION_NOT_FOUND)
        );

    // 현재 회원이 공고 작성자인지 확인
    if (!currentMember.getMemberId().equals(post.getAuthor().getMemberId())) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    // 공고가 심사 가능한 상태인지 확인
    PostStatus postStatus = post.getStatus();

    if (postStatus != PostStatus.RECRUITING && postStatus != PostStatus.RECEPTION_CLOSED) {
      throw new BusinessException(ApplicationErrorCode.POST_ALREADY_CLOSED);
    }

    // 신청 상태를 검증하고 심사 시작
    application.startReview();

    // 신청 상태 변경과 Auditing 수정 시각을 DB에 반영
    applicationRepository.flush();

    // 심사를 시작한 신청 정보 반환
    return new ApplicationResponseDto.ApplicationReviewResponse(
        application.getApplicationId(),
        application.getStatus(),
        toResponseTime(application.getUpdatedAt())
    );
  }

  /**
   * 신청 취소 후 접수 마감 여부와 남은 신청 상태에 따라 공고 상태를 갱신한다.
   * 호출자는 공고 잠금을 보유하고 취소된 신청을 flush한 상태여야 한다.
   *
   * @param post 잠금 조회한 공고
   * @throws IllegalStateException 마감된 미종료 공고에 승인 또는 완료된 신청이 있는 경우
   */
  private void updatePostStatusAfterCancellation(Post post) {
    // 이미 종료된 공고의 상태와 종료 정보 유지
    if (post.getStatus() == PostStatus.CLOSED) return;

    // 마감 판단에 사용할 현재 UTC 시각 생성
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

    // 접수 마감 전이면 공고 상태 유지
    if (!post.isRecruitmentDeadlineReached(now)) return;

    // 승인 또는 완료된 신청을 잠금 조회
    List<Application> selectedApplications = applicationRepository.findByPostIdAndStatusInForUpdate(
        post.getPostId(),
        List.of(ApplicationStatus.APPROVED, ApplicationStatus.COMPLETED)
    );

    // 미종료 공고에 선정된 신청이 있는 상태 불일치 차단
    if (!selectedApplications.isEmpty()) {
      throw new IllegalStateException("종료되지 않은 공고에 승인 또는 완료된 신청이 존재합니다.");
    }

    // 접수 또는 심사 중인 신청을 잠금 조회
    List<Application> pendingApplications = applicationRepository.findByPostIdAndStatusInForUpdate(
        post.getPostId(),
        List.of(ApplicationStatus.APPLIED, ApplicationStatus.UNDER_REVIEW)
    );

    // 선정 후보가 남아 있으면 접수만 마감
    if (!pendingApplications.isEmpty()) {
      post.closeReception();
      return;
    }

    // 선정 후보가 없으면 미선정 사유로 공고 종료
    post.closeAfterDeadline(PostClosedReason.NO_SELECTION, now);
  }

  /**
   * 회원을 잠금 조회하고 신청 가능한 상태와 역할인지 확인한다.
   *
   * @param memberId 신청할 회원 ID
   * @return 신청 가능한 {@link Member}
   * @throws BusinessException 회원이 없거나 활성 상태가 아니거나 신청 권한이 없는 경우
   */
  private Member getApplicantForUpdate(Long memberId) {
    // 신청자를 잠금 조회
    Member applicant = memberRepository.findByIdForUpdate(memberId)
        .orElseThrow(
            () -> new BusinessException(CommonErrorCode.UNAUTHORIZED)
        );

    // 활성 회원인지 확인
    if (applicant.getStatus() != MemberStatus.ACTIVE) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }

    // 신청 가능한 역할인지 확인
    MemberRole role = applicant.getRole();

    if (role != MemberRole.ROLE_USER && role != MemberRole.ROLE_PROTECTOR) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    // 검증을 통과한 applicant 반환
    return applicant;
  }

  /**
   * UTC 기준 시각을 동일한 순간의 한국 시각으로 변환한다.
   *
   * @param dateTime UTC 기준 시각, null 가능
   * @return +09:00으로 변환한 시각, 입력이 null이면 null
   */
  private OffsetDateTime toResponseTime(LocalDateTime dateTime) {
    if (dateTime == null) {
      return null;
    }
    return dateTime.atOffset(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(9));
  }
}
