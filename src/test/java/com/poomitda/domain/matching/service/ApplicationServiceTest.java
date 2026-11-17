package com.poomitda.domain.matching.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.poomitda.domain.matching.dto.ApplicationRequestDto.CreateApplicationRequest;
import com.poomitda.domain.matching.dto.ApplicationResponseDto.CreateApplicationResponse;
import com.poomitda.domain.matching.entity.Application;
import com.poomitda.domain.matching.entity.constant.*;
import com.poomitda.domain.matching.exception.ApplicationErrorCode;
import com.poomitda.domain.matching.repository.ApplicationRepository;
import com.poomitda.domain.matching.validation.ApplicationCreateValidator;
import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.entity.constant.MemberRole;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.domain.post.entity.Post;
import com.poomitda.domain.post.entity.constant.NoticeType;
import com.poomitda.domain.post.repository.PostRepository;
import com.poomitda.global.constant.*;
import com.poomitda.global.exception.*;
import com.poomitda.global.exception.exception.BusinessException;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
}