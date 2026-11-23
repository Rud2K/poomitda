package com.poomitda.domain.matching.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.poomitda.domain.matching.dto.ApplicationRequestDto.CreateApplicationRequest;
import com.poomitda.domain.matching.dto.ApplicationResponseDto.*;
import com.poomitda.domain.matching.entity.Application;
import com.poomitda.domain.matching.entity.constant.*;
import com.poomitda.domain.matching.exception.ApplicationErrorCode;
import com.poomitda.domain.matching.repository.ApplicationRepository;
import com.poomitda.domain.matching.validation.ApplicationCreateValidator;
import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.entity.constant.MemberRole;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.domain.post.entity.Post;
import com.poomitda.domain.post.entity.constant.*;
import com.poomitda.domain.post.exception.PostErrorCode;
import com.poomitda.domain.post.repository.PostRepository;
import com.poomitda.global.constant.*;
import com.poomitda.global.exception.*;
import com.poomitda.global.exception.exception.BusinessException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class ApplicationServiceTest {

  private static final Long MEMBER_ID = 1L;
  private static final Long POST_ID = 100L;
  private static final Long APPLICATION_ID = 301L;
  private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 17, 1, 20);

  private final MemberRepository memberRepository = mock(MemberRepository.class);
  private final PostRepository postRepository = mock(PostRepository.class);
  private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);

  private final ApplicationService applicationService = new ApplicationService(
      memberRepository,
      postRepository,
      applicationRepository,
      new ApplicationCreateValidator()
  );

  @ParameterizedTest
  @EnumSource(
      value = MemberRole.class,
      names = {"ROLE_USER", "ROLE_PROTECTOR"}
  )
  @DisplayName("신청 가능한 회원의 정상 요청 -> 신청 저장 및 응답 반환")
  void createsApplication(MemberRole role) {
    // given
    Member applicant = createMember(MEMBER_ID);
    ReflectionTestUtils.setField(applicant, "role", role);

    Post post = createPost(createMember(2L), null);
    CreateApplicationRequest request = createRequest(
        HousingOwnership.MONTHLY_RENT, false
    );

    when(memberRepository.findByIdForUpdate(MEMBER_ID))
        .thenReturn(Optional.of(applicant));
    when(postRepository.findByIdForUpdate(POST_ID))
        .thenReturn(Optional.of(post));
    when(applicationRepository.existsByPost_PostIdAndApplicant_MemberId(
        POST_ID, MEMBER_ID
    )).thenReturn(false);
    when(applicationRepository.save(any(Application.class)))
        .thenAnswer(invocation -> {
          Application application = invocation.getArgument(0);
          ReflectionTestUtils.setField(
              application, "applicationId", APPLICATION_ID
          );
          ReflectionTestUtils.setField(application, "createdAt", CREATED_AT);
          return application;
        });

    // when
    CreateApplicationResponse response =
        applicationService.createApplication(MEMBER_ID, request);

    // then
    ArgumentCaptor<Application> captor =
        ArgumentCaptor.forClass(Application.class);
    verify(applicationRepository).save(captor.capture());

    Application saved = captor.getValue();

    assertAll(
        () -> assertSame(post, saved.getPost()),
        () -> assertSame(applicant, saved.getApplicant()),
        () -> assertEquals(request.occupation(), saved.getOccupation()),
        () -> assertEquals(request.housingType(), saved.getHousingType()),
        () -> assertEquals(
            request.housingOwnership(), saved.getHousingOwnership()
        ),
        () -> assertEquals(Boolean.FALSE, saved.getIsLandlordAgreed()),
        () -> assertFalse(saved.isHasYardOrBalcony()),
        () -> assertEquals(8, saved.getTimeAwayPerDay()),
        () -> assertEquals(2, saved.getFamilyCount()),
        () -> assertEquals("30대 2명", saved.getFamilyAges()),
        () -> assertTrue(saved.isHasCohabitantAgreed()),
        () -> assertFalse(saved.isHasAllergy()),
        () -> assertEquals("없음", saved.getCurrentPets()),
        () -> assertEquals("강아지 양육 경험 있음", saved.getPastExperienceDetail()),
        () -> assertTrue(saved.isFinanciallyReady()),
        () -> assertTrue(saved.isPledgeAgreed()),
        () -> assertEquals(ApplicationStatus.APPLIED, saved.getStatus()),
        () -> assertNull(saved.getCompletedBy()),
        () -> assertNull(saved.getCompletionMethod()),
        () -> assertNull(saved.getCompletedAt()),
        () -> assertNull(saved.getApprovedAt()),
        () -> assertNull(saved.getRejectionReason()),
        () -> assertNull(saved.getRejectedAt()),
        () -> assertEquals(APPLICATION_ID, response.applicationId()),
        () -> assertEquals(POST_ID, response.postId()),
        () -> assertEquals(ApplicationStatus.APPLIED, response.status()),
        () -> assertEquals(
            OffsetDateTime.parse("2026-11-17T10:20:00+09:00"),
            response.createdAt()
        )
    );
  }

  @Test
  @DisplayName("본인이 작성한 공고 -> 신청 거절")
  void rejectsOwnPost() {
    // given
    Member applicant = createMember(MEMBER_ID);
    Post post = createPost(applicant, null);

    when(memberRepository.findByIdForUpdate(MEMBER_ID))
        .thenReturn(Optional.of(applicant));
    when(postRepository.findByIdForUpdate(POST_ID))
        .thenReturn(Optional.of(post));

    // when & then
    assertRejected(
        createRequest(HousingOwnership.OWNED, null),
        ApplicationErrorCode.CANNOT_APPLY_OWN_POST
    );
  }

  @Test
  @DisplayName("기존 신청 이력 존재 -> 중복 신청 거절")
  void rejectsDuplicateApplication() {
    // given
    Member applicant = createMember(MEMBER_ID);
    Post post = createPost(createMember(2L), null);

    when(memberRepository.findByIdForUpdate(MEMBER_ID))
        .thenReturn(Optional.of(applicant));
    when(postRepository.findByIdForUpdate(POST_ID))
        .thenReturn(Optional.of(post));
    when(applicationRepository.existsByPost_PostIdAndApplicant_MemberId(
        POST_ID, MEMBER_ID
    )).thenReturn(true);

    // when & then
    assertRejected(
        createRequest(HousingOwnership.OWNED, null),
        ApplicationErrorCode.APPLICATION_ALREADY_EXISTS
    );
  }

  @Test
  @DisplayName("접수 종료된 공고 -> 신청 거절")
  void rejectsNonRecruitingPost() {
    // given
    Member applicant = createMember(MEMBER_ID);
    Post post = createPost(createMember(2L), null);
    post.closeReception();

    when(memberRepository.findByIdForUpdate(MEMBER_ID))
        .thenReturn(Optional.of(applicant));
    when(postRepository.findByIdForUpdate(POST_ID))
        .thenReturn(Optional.of(post));

    // when & then
    assertRejected(
        createRequest(HousingOwnership.OWNED, null),
        ApplicationErrorCode.POST_NOT_RECRUITING
    );
  }

  @Test
  @DisplayName("모집 중이지만 접수 마감 경과 -> 신청 거절")
  void rejectsExpiredPost() {
    // given
    Member applicant = createMember(MEMBER_ID);
    Post post = createPost(
        createMember(2L),
        LocalDateTime.now(ZoneOffset.UTC).minusDays(1)
    );

    when(memberRepository.findByIdForUpdate(MEMBER_ID))
        .thenReturn(Optional.of(applicant));
    when(postRepository.findByIdForUpdate(POST_ID))
        .thenReturn(Optional.of(post));
    when(applicationRepository.existsByPost_PostIdAndApplicant_MemberId(
        POST_ID, MEMBER_ID
    )).thenReturn(false);

    // when & then
    assertRejected(
        createRequest(HousingOwnership.OWNED, null),
        ApplicationErrorCode.APPLICATION_DEADLINE_PASSED
    );
  }

  @ParameterizedTest
  @EnumSource(
      value = HousingOwnership.class,
      names = {"JEONSE", "MONTHLY_RENT"}
  )
  @DisplayName("전세 또는 월세에서 임대인 동의 여부 누락 -> 입력 오류")
  void rejectsMissingLandlordAgreement(HousingOwnership ownership) {
    // given
    CreateApplicationRequest request = createRequest(ownership, null);

    // when & then
    assertRejected(request, CommonErrorCode.INVALID_INPUT_VALUE);
    verifyNoInteractions(memberRepository, postRepository);
  }

  @ParameterizedTest
  @ValueSource(longs = {1L, 2L})
  @DisplayName("신청자 또는 공고 작성자 -> 신청 상세 반환")
  void getsApplicationDetailForParticipant(long currentMemberId) {
    // given
    Member applicant = createMember(MEMBER_ID);
    Member author = createMember(2L);
    ReflectionTestUtils.setField(author, "role", MemberRole.ROLE_PROTECTOR);

    Post post = createPost(author, null);
    Application application = createDetailApplication(post, applicant);
    Member currentMember = currentMemberId == MEMBER_ID ? applicant : author;

    when(memberRepository.findById(currentMemberId))
        .thenReturn(Optional.of(currentMember));
    when(applicationRepository.findByApplicationId(APPLICATION_ID))
        .thenReturn(Optional.of(application));

    // when
    ApplicationDetailResponse response =
        applicationService.getApplicationDetail(currentMemberId, APPLICATION_ID);

    // then
    assertAll(
        () -> assertEquals(APPLICATION_ID, response.applicationId()),
        () -> assertEquals(ApplicationStatus.APPLIED, response.status()),
        () -> assertEquals(MEMBER_ID, response.applicant().memberId()),
        () -> assertEquals(applicant.getNickname(), response.applicant().nickname()),
        () -> assertEquals(POST_ID, response.post().postId()),
        () -> assertEquals(post.getTitle(), response.post().title()),
        () -> assertEquals(post.getNoticeType(), response.post().noticeType()),
        () -> assertEquals(post.getSpecies(), response.post().species()),
        () -> assertEquals(post.getStatus(), response.post().status()),
        () -> assertEquals("회사원", response.occupation()),
        () -> assertEquals(HousingType.APARTMENT, response.housingType()),
        () -> assertEquals(HousingOwnership.MONTHLY_RENT, response.housingOwnership()),
        () -> assertEquals(Boolean.FALSE, response.isLandlordAgreed()),
        () -> assertFalse(response.hasYardOrBalcony()),
        () -> assertEquals(8, response.timeAwayPerDay()),
        () -> assertEquals(2, response.familyCount()),
        () -> assertEquals("30대 2명", response.familyAges()),
        () -> assertTrue(response.hasCohabitantAgreed()),
        () -> assertFalse(response.hasAllergy()),
        () -> assertEquals("없음", response.currentPets()),
        () -> assertEquals("강아지 양육 경험 있음", response.pastExperienceDetail()),
        () -> assertTrue(response.isFinanciallyReady()),
        () -> assertTrue(response.pledgeAgreed()),
        () -> assertEquals(
            OffsetDateTime.parse("2026-11-17T10:20:00+09:00"),
            response.createdAt()
        ),
        () -> assertEquals(
            OffsetDateTime.parse("2026-11-17T11:20:00+09:00"),
            response.updatedAt()
        ),
        () -> assertNull(response.approvedAt()),
        () -> assertNull(response.completedAt()),
        () -> assertNull(response.completionMethod()),
        () -> assertNull(response.rejectionReason()),
        () -> assertNull(response.rejectedAt())
    );
  }

  @Test
  @DisplayName("신청자와 공고 작성자가 아닌 회원 -> 조회 권한 오류")
  void rejectsApplicationDetailForNonParticipant() {
    // given
    Member applicant = createMember(MEMBER_ID);
    Post post = createPost(createMember(2L), null);
    Application application = createDetailApplication(post, applicant);
    Member otherMember = createMember(3L);

    when(memberRepository.findById(3L))
        .thenReturn(Optional.of(otherMember));
    when(applicationRepository.findByApplicationId(APPLICATION_ID))
        .thenReturn(Optional.of(application));

    // when & then
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> applicationService.getApplicationDetail(3L, APPLICATION_ID)
    );

    assertEquals(CommonErrorCode.FORBIDDEN, exception.getErrorCode());
  }

  @Test
  @DisplayName("존재하지 않는 신청 -> 신청 없음 오류")
  void rejectsMissingApplicationDetail() {
    // given
    Member currentMember = createMember(MEMBER_ID);

    when(memberRepository.findById(MEMBER_ID))
        .thenReturn(Optional.of(currentMember));
    when(applicationRepository.findByApplicationId(APPLICATION_ID))
        .thenReturn(Optional.empty());

    // when & then
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> applicationService.getApplicationDetail(MEMBER_ID, APPLICATION_ID)
    );

    assertEquals(
        ApplicationErrorCode.APPLICATION_NOT_FOUND,
        exception.getErrorCode()
    );
  }

  @Test
  @DisplayName("마감 전 본인의 접수 상태 신청 -> 취소 응답 반환 및 모집 상태 유지")
  void cancelsOwnAppliedApplicationBeforeDeadline() {
    // given
    Member applicant = createMember(MEMBER_ID);
    Post post = createPost(
        createMember(2L),
        LocalDateTime.now(ZoneOffset.UTC).plusDays(1)
    );
    Application application = createDetailApplication(post, applicant);
    LocalDateTime updatedAt = CREATED_AT.plusDays(1);

    when(memberRepository.findByIdForUpdate(MEMBER_ID))
        .thenReturn(Optional.of(applicant));
    when(applicationRepository.findPostIdByApplicationId(APPLICATION_ID))
        .thenReturn(Optional.of(POST_ID));
    when(postRepository.findByIdIncludingDeletedForUpdate(POST_ID))
        .thenReturn(Optional.of(post));
    when(applicationRepository.findByIdForUpdate(APPLICATION_ID))
        .thenReturn(Optional.of(application));

    doAnswer(invocation -> {
      ReflectionTestUtils.setField(application, "updatedAt", updatedAt);
      return null;
    }).when(applicationRepository).flush();

    // when
    ApplicationCancelResponse response = applicationService.cancelApplication(MEMBER_ID, APPLICATION_ID);

    // then
    assertAll(
        () -> assertEquals(ApplicationStatus.CANCELLED, application.getStatus()),
        () -> assertEquals(APPLICATION_ID, response.applicationId()),
        () -> assertEquals(ApplicationStatus.CANCELLED, response.status()),
        () -> assertEquals(
            OffsetDateTime.parse("2026-11-18T10:20:00+09:00"),
            response.updatedAt()
        ),
        () -> assertEquals(PostStatus.RECRUITING, post.getStatus()),
        () -> assertNull(post.getClosedAt()),
        () -> assertNull(post.getClosedReason())
    );

    verify(applicationRepository).flush();
    verify(applicationRepository, never())
        .findByPostIdAndStatusInForUpdate(anyLong(), anyCollection());
  }

  @ParameterizedTest
  @EnumSource(
      value = ApplicationStatus.class,
      names = "APPLIED",
      mode = EnumSource.Mode.EXCLUDE
  )
  @DisplayName("접수 상태가 아닌 본인 신청 -> 취소 불가 오류 및 상태 유지")
  void rejectsCancellationForNonAppliedStatus(ApplicationStatus status) {
    // given
    Member applicant = createMember(MEMBER_ID);
    Post post = createPost(createMember(2L), null);
    Application application = createDetailApplication(post, applicant);

    if (status == ApplicationStatus.CANCELLED) {
      application.cancel();
    } else {
      ReflectionTestUtils.setField(application, "status", status);
    }

    when(memberRepository.findByIdForUpdate(MEMBER_ID))
        .thenReturn(Optional.of(applicant));
    when(applicationRepository.findPostIdByApplicationId(APPLICATION_ID))
        .thenReturn(Optional.of(POST_ID));
    when(postRepository.findByIdIncludingDeletedForUpdate(POST_ID))
        .thenReturn(Optional.of(post));
    when(applicationRepository.findByIdForUpdate(APPLICATION_ID))
        .thenReturn(Optional.of(application));

    // when & then
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> applicationService.cancelApplication(MEMBER_ID, APPLICATION_ID)
    );

    assertAll(
        () -> assertEquals(
            ApplicationErrorCode.APPLICATION_CANNOT_BE_CANCELLED,
            exception.getErrorCode()
        ),
        () -> assertEquals(status, application.getStatus()),
        () -> assertEquals(PostStatus.RECRUITING, post.getStatus())
    );

    verify(applicationRepository, never()).flush();
    verify(applicationRepository, never())
        .findByPostIdAndStatusInForUpdate(anyLong(), anyCollection());
  }

  @ParameterizedTest
  @ValueSource(longs = {2L, 3L})
  @DisplayName("공고 작성자 또는 제삼자의 취소 요청 -> 권한 오류 및 신청 상태 유지")
  void rejectsCancellationForNonApplicant(long currentMemberId) {
    // given
    Member applicant = createMember(MEMBER_ID);
    Member author = createMember(2L);
    ReflectionTestUtils.setField(author, "role", MemberRole.ROLE_PROTECTOR);

    Post post = createPost(author, null);
    Application application = createDetailApplication(post, applicant);
    Member currentMember = currentMemberId == 2L
        ? author
        : createMember(currentMemberId);

    when(memberRepository.findByIdForUpdate(currentMemberId))
        .thenReturn(Optional.of(currentMember));
    when(applicationRepository.findPostIdByApplicationId(APPLICATION_ID))
        .thenReturn(Optional.of(POST_ID));
    when(postRepository.findByIdIncludingDeletedForUpdate(POST_ID))
        .thenReturn(Optional.of(post));
    when(applicationRepository.findByIdForUpdate(APPLICATION_ID))
        .thenReturn(Optional.of(application));

    // when & then
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> applicationService.cancelApplication(
            currentMemberId, APPLICATION_ID
        )
    );

    assertAll(
        () -> assertEquals(CommonErrorCode.FORBIDDEN, exception.getErrorCode()),
        () -> assertEquals(ApplicationStatus.APPLIED, application.getStatus()),
        () -> assertEquals(PostStatus.RECRUITING, post.getStatus())
    );

    verify(applicationRepository, never()).flush();
    verify(applicationRepository, never())
        .findByPostIdAndStatusInForUpdate(anyLong(), anyCollection());
  }

  @Test
  @DisplayName("접수 마감 후 마지막 후보의 신청 취소 -> 미선정 사유로 공고 종료")
  void closesExpiredPostAfterLastApplicationCancelled() {
    // given
    Member applicant = createMember(MEMBER_ID);
    Post post = createPost(
        createMember(2L),
        LocalDateTime.now(ZoneOffset.UTC).minusDays(1)
    );
    post.closeReception();
    Application application = createDetailApplication(post, applicant);

    when(memberRepository.findByIdForUpdate(MEMBER_ID))
        .thenReturn(Optional.of(applicant));
    when(applicationRepository.findPostIdByApplicationId(APPLICATION_ID))
        .thenReturn(Optional.of(POST_ID));
    when(postRepository.findByIdIncludingDeletedForUpdate(POST_ID))
        .thenReturn(Optional.of(post));
    when(applicationRepository.findByIdForUpdate(APPLICATION_ID))
        .thenReturn(Optional.of(application));
    when(applicationRepository.findByPostIdAndStatusInForUpdate(
        POST_ID,
        List.of(ApplicationStatus.APPROVED, ApplicationStatus.COMPLETED)
    )).thenReturn(List.of());
    when(applicationRepository.findByPostIdAndStatusInForUpdate(
        POST_ID,
        List.of(ApplicationStatus.APPLIED, ApplicationStatus.UNDER_REVIEW)
    )).thenReturn(List.of());

    // when
    LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
    ApplicationCancelResponse response = applicationService.cancelApplication(MEMBER_ID, APPLICATION_ID);
    LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);

    // then
    assertAll(
        () -> assertEquals(ApplicationStatus.CANCELLED, application.getStatus()),
        () -> assertEquals(ApplicationStatus.CANCELLED, response.status()),
        () -> assertEquals(PostStatus.CLOSED, post.getStatus()),
        () -> assertEquals(PostClosedReason.NO_SELECTION, post.getClosedReason())
    );
    assertNotNull(post.getClosedAt());
    assertFalse(post.getClosedAt().isBefore(before));
    assertFalse(post.getClosedAt().isAfter(after));

    verify(applicationRepository).flush();
  }

  @Test
  @DisplayName("접수 마감 후 다른 후보가 남은 신청 취소 -> 접수만 마감")
  void closesOnlyReceptionWhenAnotherCandidateRemains() {
    // given
    Member applicant = createMember(MEMBER_ID);
    Post post = createPost(
        createMember(2L),
        LocalDateTime.now(ZoneOffset.UTC).minusDays(1)
    );
    Application application = createDetailApplication(post, applicant);
    Application remainingApplication = createDetailApplication(post, createMember(3L));
    ReflectionTestUtils.setField(
        remainingApplication, "applicationId", APPLICATION_ID + 1
    );

    when(memberRepository.findByIdForUpdate(MEMBER_ID))
        .thenReturn(Optional.of(applicant));
    when(applicationRepository.findPostIdByApplicationId(APPLICATION_ID))
        .thenReturn(Optional.of(POST_ID));
    when(postRepository.findByIdIncludingDeletedForUpdate(POST_ID))
        .thenReturn(Optional.of(post));
    when(applicationRepository.findByIdForUpdate(APPLICATION_ID))
        .thenReturn(Optional.of(application));
    when(applicationRepository.findByPostIdAndStatusInForUpdate(
        POST_ID,
        List.of(ApplicationStatus.APPROVED, ApplicationStatus.COMPLETED)
    )).thenReturn(List.of());
    when(applicationRepository.findByPostIdAndStatusInForUpdate(
        POST_ID,
        List.of(ApplicationStatus.APPLIED, ApplicationStatus.UNDER_REVIEW)
    )).thenReturn(List.of(remainingApplication));

    // when
    ApplicationCancelResponse response = applicationService.cancelApplication(MEMBER_ID, APPLICATION_ID);

    // then
    assertAll(
        () -> assertEquals(ApplicationStatus.CANCELLED, application.getStatus()),
        () -> assertEquals(ApplicationStatus.CANCELLED, response.status()),
        () -> assertEquals(
            ApplicationStatus.APPLIED, remainingApplication.getStatus()
        ),
        () -> assertEquals(PostStatus.RECEPTION_CLOSED, post.getStatus()),
        () -> assertNull(post.getClosedReason()),
        () -> assertNull(post.getClosedAt())
    );

    verify(applicationRepository).flush();
  }

  @Test
  @DisplayName("삭제된 공고의 본인 접수 상태 신청 -> 취소 허용")
  void cancelsApplicationForDeletedPost() {
    // given
    Member applicant = createMember(MEMBER_ID);
    Post post = createPost(createMember(2L), null);
    LocalDateTime deletedAt = CREATED_AT.plusHours(2);
    ReflectionTestUtils.setField(post, "deletedAt", deletedAt);

    Application application = createDetailApplication(post, applicant);
    stubCancellationLookup(applicant, post, application);

    // when
    ApplicationCancelResponse response = applicationService.cancelApplication(MEMBER_ID, APPLICATION_ID);

    // then
    assertAll(
        () -> assertEquals(ApplicationStatus.CANCELLED, application.getStatus()),
        () -> assertEquals(ApplicationStatus.CANCELLED, response.status()),
        () -> assertEquals(deletedAt, post.getDeletedAt()),
        () -> assertEquals(PostStatus.RECRUITING, post.getStatus())
    );

    verify(postRepository).findByIdIncludingDeletedForUpdate(POST_ID);
    verify(applicationRepository).flush();
  }

  @Test
  @DisplayName("이미 종료된 공고의 신청 취소 -> 기존 종료 사유와 시각 유지")
  void preservesClosedPostInformationOnCancellation() {
    // given
    Member applicant = createMember(MEMBER_ID);
    LocalDateTime closedAt = CREATED_AT.plusDays(1);
    Post post = createPost(createMember(2L), CREATED_AT);
    post.closeAfterDeadline(PostClosedReason.NO_SELECTION, closedAt);

    Application application = createDetailApplication(post, applicant);
    stubCancellationLookup(applicant, post, application);

    // when
    ApplicationCancelResponse response = applicationService.cancelApplication(MEMBER_ID, APPLICATION_ID);

    // then
    assertAll(
        () -> assertEquals(ApplicationStatus.CANCELLED, application.getStatus()),
        () -> assertEquals(ApplicationStatus.CANCELLED, response.status()),
        () -> assertEquals(PostStatus.CLOSED, post.getStatus()),
        () -> assertEquals(PostClosedReason.NO_SELECTION, post.getClosedReason()),
        () -> assertEquals(closedAt, post.getClosedAt())
    );

    verify(applicationRepository).flush();
    verify(applicationRepository, never())
        .findByPostIdAndStatusInForUpdate(anyLong(), anyCollection());
  }

  @Test
  @DisplayName("존재하지 않는 신청의 취소 요청 -> 신청 없음 오류")
  void rejectsCancellationForMissingApplication() {
    // given
    Member applicant = createMember(MEMBER_ID);

    when(memberRepository.findByIdForUpdate(MEMBER_ID))
        .thenReturn(Optional.of(applicant));
    when(applicationRepository.findPostIdByApplicationId(APPLICATION_ID))
        .thenReturn(Optional.empty());

    // when & then
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> applicationService.cancelApplication(MEMBER_ID, APPLICATION_ID)
    );

    assertEquals(
        ApplicationErrorCode.APPLICATION_NOT_FOUND,
        exception.getErrorCode()
    );
    verifyNoInteractions(postRepository);
    verify(applicationRepository, never()).findByIdForUpdate(anyLong());
    verify(applicationRepository, never()).flush();
  }

  @ParameterizedTest
  @EnumSource(
      value = ApplicationStatus.class,
      names = {"APPROVED", "COMPLETED"}
  )
  @DisplayName("마감된 미종료 공고에 선정된 신청 존재 -> 상태 불일치 예외")
  void rejectsInconsistentExpiredPostOnCancellation(
      ApplicationStatus selectedStatus
  ) {
    // given
    Member applicant = createMember(MEMBER_ID);
    Post post = createPost(
        createMember(2L),
        LocalDateTime.now(ZoneOffset.UTC).minusDays(1)
    );
    Application application = createDetailApplication(post, applicant);
    Application selectedApplication = createDetailApplication(post, createMember(3L));

    ReflectionTestUtils.setField(
        selectedApplication, "applicationId", APPLICATION_ID + 1
    );
    ReflectionTestUtils.setField(
        selectedApplication, "status", selectedStatus
    );

    stubCancellationLookup(applicant, post, application);
    when(applicationRepository.findByPostIdAndStatusInForUpdate(
        POST_ID,
        List.of(ApplicationStatus.APPROVED, ApplicationStatus.COMPLETED)
    )).thenReturn(List.of(selectedApplication));

    // when & then
    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> applicationService.cancelApplication(MEMBER_ID, APPLICATION_ID)
    );

    assertAll(
        () -> assertEquals(
            "종료되지 않은 공고에 승인 또는 완료된 신청이 존재합니다.",
            exception.getMessage()
        ),
        () -> assertEquals(PostStatus.RECRUITING, post.getStatus()),
        () -> assertNull(post.getClosedReason()),
        () -> assertNull(post.getClosedAt())
    );

    verify(applicationRepository, never())
        .findByPostIdAndStatusInForUpdate(
            POST_ID,
            List.of(ApplicationStatus.APPLIED, ApplicationStatus.UNDER_REVIEW)
        );
  }

  @ParameterizedTest
  @CsvSource({
      "RECRUITING, false",
      "RECRUITING, true",
      "RECEPTION_CLOSED, true"
  })
  @DisplayName("심사 가능한 공고의 접수 신청 -> 마감 여부와 관계없이 심사 시작")
  void reviewStartsForEligiblePost(
      PostStatus postStatus,
      boolean deadlinePassed
  ) {
    // given
    Member author = createReviewProtector(2L);
    LocalDateTime deadline = deadlinePassed
        ? LocalDateTime.now(ZoneOffset.UTC).minusDays(1)
        : LocalDateTime.now(ZoneOffset.UTC).plusDays(1);

    Post post = createPost(author, deadline);
    if (postStatus == PostStatus.RECEPTION_CLOSED) {
      post.closeReception();
    }

    Application application = createDetailApplication(post, createMember(MEMBER_ID));
    stubReviewLookup(author, post, application);

    LocalDateTime updatedAt = CREATED_AT.plusDays(1);
    doAnswer(invocation -> {
      ReflectionTestUtils.setField(application, "updatedAt", updatedAt);
      return null;
    }).when(applicationRepository).flush();

    // when
    ApplicationReviewResponse response = applicationService.reviewApplication(2L, APPLICATION_ID);

    // then
    assertAll(
        () -> assertEquals(
            ApplicationStatus.UNDER_REVIEW, application.getStatus()
        ),
        () -> assertEquals(APPLICATION_ID, response.applicationId()),
        () -> assertEquals(
            ApplicationStatus.UNDER_REVIEW, response.status()
        ),
        () -> assertEquals(
            OffsetDateTime.parse("2026-11-18T10:20:00+09:00"),
            response.updatedAt()
        ),
        () -> assertEquals(postStatus, post.getStatus()),
        () -> assertNull(post.getClosedAt()),
        () -> assertNull(post.getClosedReason())
    );
    verify(applicationRepository).flush();
    verify(postRepository, never())
        .findByIdIncludingDeletedForUpdate(anyLong());
  }

  @ParameterizedTest
  @ValueSource(strings = {"NULL_ID", "MISSING_MEMBER", "WITHDRAWN_MEMBER"})
  @DisplayName("인증 가능한 활성 회원이 아님 -> UNAUTHORIZED")
  void reviewRejectsInvalidAuthentication(String scenario) {
    // given
    Long currentMemberId = "NULL_ID".equals(scenario) ? null : 2L;

    if ("MISSING_MEMBER".equals(scenario)) {
      when(memberRepository.findByIdForUpdate(2L))
          .thenReturn(Optional.empty());
    } else if ("WITHDRAWN_MEMBER".equals(scenario)) {
      Member author = createReviewProtector(2L);
      author.withdraw(CREATED_AT);
      when(memberRepository.findByIdForUpdate(2L))
          .thenReturn(Optional.of(author));
    }

    // when & then
    assertReviewRejected(
        currentMemberId,
        APPLICATION_ID,
        CommonErrorCode.UNAUTHORIZED
    );
    verifyNoInteractions(postRepository, applicationRepository);
  }

  @ParameterizedTest
  @EnumSource(
      value = MemberRole.class,
      names = {"ROLE_USER", "ROLE_ADMIN"}
  )
  @DisplayName("현재 역할이 보호자가 아님 -> FORBIDDEN")
  void reviewRejectsNonProtector(MemberRole role) {
    // given
    Member currentMember = createMember(2L);
    ReflectionTestUtils.setField(currentMember, "role", role);
    when(memberRepository.findByIdForUpdate(2L))
        .thenReturn(Optional.of(currentMember));

    // when & then
    assertReviewRejected(
        2L, APPLICATION_ID, CommonErrorCode.FORBIDDEN
    );
    verifyNoInteractions(postRepository, applicationRepository);
  }

  @Test
  @DisplayName("다른 보호자의 공고 -> FORBIDDEN 및 신청 상태 유지")
  void reviewRejectsNonAuthor() {
    // given
    Member author = createReviewProtector(2L);
    Member otherProtector = createReviewProtector(3L);
    Post post = createPost(author, null);
    Application application = createDetailApplication(post, createMember(MEMBER_ID));

    stubReviewLookup(otherProtector, post, application);

    // when & then
    assertReviewRejected(
        3L, APPLICATION_ID, CommonErrorCode.FORBIDDEN
    );
    assertEquals(ApplicationStatus.APPLIED, application.getStatus());
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = {0L, -1L})
  @DisplayName("신청 ID가 null 또는 양수가 아님 -> INVALID_INPUT_VALUE")
  void reviewRejectsInvalidApplicationId(Long applicationId) {
    // given
    Member author = createReviewProtector(2L);
    when(memberRepository.findByIdForUpdate(2L))
        .thenReturn(Optional.of(author));

    // when & then
    assertReviewRejected(
        2L, applicationId, CommonErrorCode.INVALID_INPUT_VALUE
    );
    verifyNoInteractions(postRepository, applicationRepository);
  }

  @Test
  @DisplayName("신청이 존재하지 않음 -> APPLICATION_NOT_FOUND")
  void reviewRejectsMissingApplication() {
    // given
    Member author = createReviewProtector(2L);
    when(memberRepository.findByIdForUpdate(2L))
        .thenReturn(Optional.of(author));
    when(applicationRepository.findPostIdByApplicationId(APPLICATION_ID))
        .thenReturn(Optional.empty());

    // when & then
    assertReviewRejected(
        2L, APPLICATION_ID, ApplicationErrorCode.APPLICATION_NOT_FOUND
    );
    verifyNoInteractions(postRepository);
    verify(applicationRepository, never()).findByIdForUpdate(anyLong());
  }

  @Test
  @DisplayName("삭제 제외 공고 조회 결과가 없음 -> POST_NOT_FOUND")
  void reviewRejectsUnavailablePost() {
    // given
    Member author = createReviewProtector(2L);
    when(memberRepository.findByIdForUpdate(2L))
        .thenReturn(Optional.of(author));
    when(applicationRepository.findPostIdByApplicationId(APPLICATION_ID))
        .thenReturn(Optional.of(POST_ID));
    when(postRepository.findByIdForUpdate(POST_ID))
        .thenReturn(Optional.empty());

    // when & then
    assertReviewRejected(
        2L, APPLICATION_ID, PostErrorCode.POST_NOT_FOUND
    );
    verify(postRepository).findByIdForUpdate(POST_ID);
    verify(postRepository, never()).findByIdIncludingDeletedForUpdate(anyLong());
    verify(applicationRepository, never()).findByIdForUpdate(anyLong());
  }

  @Test
  @DisplayName("모집 절차가 종료된 공고 -> POST_ALREADY_CLOSED 및 상태 유지")
  void reviewRejectsClosedPost() {
    // given
    Member author = createReviewProtector(2L);
    Post post = createPost(author, CREATED_AT);
    LocalDateTime closedAt = CREATED_AT.plusDays(1);
    post.closeAfterDeadline(PostClosedReason.NO_SELECTION, closedAt);

    Application application = createDetailApplication(post, createMember(MEMBER_ID));
    stubReviewLookup(author, post, application);

    // when & then
    assertReviewRejected(
        2L, APPLICATION_ID, ApplicationErrorCode.POST_ALREADY_CLOSED
    );
    assertAll(
        () -> assertEquals(
            ApplicationStatus.APPLIED, application.getStatus()
        ),
        () -> assertEquals(PostStatus.CLOSED, post.getStatus()),
        () -> assertEquals(
            PostClosedReason.NO_SELECTION, post.getClosedReason()
        ),
        () -> assertEquals(closedAt, post.getClosedAt())
    );
  }

  @ParameterizedTest
  @EnumSource(
      value = ApplicationStatus.class,
      names = "APPLIED",
      mode = EnumSource.Mode.EXCLUDE
  )
  @DisplayName("접수 상태가 아닌 신청 -> APPLICATION_CANNOT_BE_REVIEWED 및 상태 유지")
  void reviewRejectsNonAppliedStatus(ApplicationStatus status) {
    // given
    Member author = createReviewProtector(2L);
    Post post = createPost(author, null);
    Application application = createDetailApplication(post, createMember(MEMBER_ID));

    if (status == ApplicationStatus.UNDER_REVIEW) {
      application.startReview();
    } else if (status == ApplicationStatus.CANCELLED) {
      application.cancel();
    } else {
      ReflectionTestUtils.setField(application, "status", status);
    }

    stubReviewLookup(author, post, application);

    // when & then
    assertReviewRejected(
        2L,
        APPLICATION_ID,
        ApplicationErrorCode.APPLICATION_CANNOT_BE_REVIEWED
    );
    assertAll(
        () -> assertEquals(status, application.getStatus()),
        () -> assertEquals(PostStatus.RECRUITING, post.getStatus())
    );
  }

  private Member createReviewProtector(Long memberId) {
    Member member = createMember(memberId);
    ReflectionTestUtils.setField(member, "role", MemberRole.ROLE_PROTECTOR);
    return member;
  }

  private void stubReviewLookup(
      Member currentMember,
      Post post,
      Application application
  ) {
    when(memberRepository.findByIdForUpdate(currentMember.getMemberId()))
        .thenReturn(Optional.of(currentMember));
    when(applicationRepository.findPostIdByApplicationId(
        application.getApplicationId()
    )).thenReturn(Optional.of(post.getPostId()));
    when(postRepository.findByIdForUpdate(post.getPostId()))
        .thenReturn(Optional.of(post));
    when(applicationRepository.findByIdForUpdate(
        application.getApplicationId()
    )).thenReturn(Optional.of(application));
  }

  private void assertReviewRejected(
      Long memberId,
      Long applicationId,
      ErrorCode expectedErrorCode
  ) {
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> applicationService.reviewApplication(memberId, applicationId)
    );

    assertEquals(expectedErrorCode, exception.getErrorCode());
    verify(applicationRepository, never()).flush();
  }

  private void stubCancellationLookup(
      Member currentMember,
      Post post,
      Application application
  ) {
    when(memberRepository.findByIdForUpdate(currentMember.getMemberId()))
        .thenReturn(Optional.of(currentMember));
    when(applicationRepository.findPostIdByApplicationId(
        application.getApplicationId()
    )).thenReturn(Optional.of(post.getPostId()));
    when(postRepository.findByIdIncludingDeletedForUpdate(post.getPostId()))
        .thenReturn(Optional.of(post));
    when(applicationRepository.findByIdForUpdate(
        application.getApplicationId()
    )).thenReturn(Optional.of(application));
  }

  private void assertRejected(
      CreateApplicationRequest request,
      ErrorCode expectedErrorCode
  ) {
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> applicationService.createApplication(MEMBER_ID, request)
    );

    assertEquals(expectedErrorCode, exception.getErrorCode());
    verify(applicationRepository, never()).save(any(Application.class));
  }

  private Member createMember(Long memberId) {
    Member member = new Member(
        "member" + memberId + "@example.com",
        "encoded-password",
        "회원" + memberId,
        Region.SEOUL,
        Species.DOG
    );
    ReflectionTestUtils.setField(member, "memberId", memberId);
    return member;
  }

  private Post createPost(Member author, LocalDateTime deadline) {
    Post post = new Post(
        author,
        "입양 공고",
        "공고 내용",
        NoticeType.ADOPTION,
        Species.DOG,
        "믹스",
        null,
        null,
        false,
        null,
        null,
        Region.SEOUL,
        null,
        deadline
    );
    ReflectionTestUtils.setField(post, "postId", POST_ID);
    return post;
  }

  private CreateApplicationRequest createRequest(
      HousingOwnership ownership,
      Boolean landlordAgreed
  ) {
    return new CreateApplicationRequest(
        POST_ID,
        "회사원",
        HousingType.APARTMENT,
        ownership,
        landlordAgreed,
        false,
        8,
        2,
        "30대 2명",
        true,
        false,
        "없음",
        "강아지 양육 경험 있음",
        true,
        true
    );
  }

  private Application createDetailApplication(Post post, Member applicant) {
    CreateApplicationRequest request =
        createRequest(HousingOwnership.MONTHLY_RENT, false);

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

    ReflectionTestUtils.setField(application, "applicationId", APPLICATION_ID);
    ReflectionTestUtils.setField(application, "createdAt", CREATED_AT);
    ReflectionTestUtils.setField(
        application, "updatedAt", CREATED_AT.plusHours(1)
    );

    return application;
  }
}