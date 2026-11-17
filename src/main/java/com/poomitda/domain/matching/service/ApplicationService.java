package com.poomitda.domain.matching.service;

import com.poomitda.domain.matching.dto.ApplicationRequestDto.CreateApplicationRequest;
import com.poomitda.domain.matching.dto.ApplicationResponseDto;
import com.poomitda.domain.matching.dto.ApplicationResponseDto.CreateApplicationResponse;
import com.poomitda.domain.matching.entity.Application;
import com.poomitda.domain.matching.exception.ApplicationErrorCode;
import com.poomitda.domain.matching.repository.ApplicationRepository;
import com.poomitda.domain.matching.validation.ApplicationCreateValidator;
import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.entity.constant.*;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.domain.post.entity.Post;
import com.poomitda.domain.post.entity.constant.PostStatus;
import com.poomitda.domain.post.exception.PostErrorCode;
import com.poomitda.domain.post.repository.PostRepository;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import java.time.*;
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
}
