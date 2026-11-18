package com.poomitda.domain.matching.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.poomitda.domain.matching.cursor.*;
import com.poomitda.domain.matching.dto.ApplicationRequestDto.MyApplicationListRequest;
import com.poomitda.domain.matching.dto.ApplicationResponseDto.MyApplicationListResponse;
import com.poomitda.domain.matching.entity.Application;
import com.poomitda.domain.matching.entity.constant.*;
import com.poomitda.domain.matching.exception.ApplicationErrorCode;
import com.poomitda.domain.matching.repository.ApplicationRepository;
import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.domain.post.entity.Post;
import com.poomitda.domain.post.entity.constant.*;
import com.poomitda.domain.post.repository.PostImageRepository;
import com.poomitda.domain.post.repository.PostImageRepository.RepresentativeImageView;
import com.poomitda.global.constant.*;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class MyApplicationListServiceTest {

  private static final Long MEMBER_ID = 1L;
  private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 19, 1, 20);

  private final MemberRepository memberRepository = mock(MemberRepository.class);
  private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
  private final PostImageRepository postImageRepository = mock(PostImageRepository.class);

  private final MyApplicationCursorCodec cursorCodec = new MyApplicationCursorCodec(JsonMapper.builder().build());

  private final MyApplicationListService service = new MyApplicationListService(
      memberRepository,
      applicationRepository,
      postImageRepository,
      cursorCodec,
      new MyApplicationCursorValidator(),
      new MyApplicationCursorFactory()
  );

  @Test
  @DisplayName("조회 개수보다 많은 신청 -> 반환 범위와 마지막 반환 신청 기준 커서 생성")
  void returnsFirstPageWithCursor() {
    // given
    Member applicant = createMember(MEMBER_ID);
    Application first = createApplication(303L, 103L, applicant);
    Application second = createApplication(302L, 102L, applicant);
    Application extra = createApplication(301L, 101L, applicant);
    MyApplicationListRequest request = new MyApplicationListRequest(null, null, 2);

    when(memberRepository.findById(MEMBER_ID))
        .thenReturn(Optional.of(applicant));
    when(applicationRepository.findMyApplications(
        MEMBER_ID, null, null, null, PageRequest.of(0, 3)
    )).thenReturn(List.of(first, second, extra));

    when(postImageRepository.findRepresentativeImagesByPostIds(
        Set.of(103L, 102L)
    )).thenReturn(List.of(
        imageView(103L, "https://example.com/103.jpg")
    ));

    // when
    MyApplicationListResponse response = service.getMyApplications(MEMBER_ID, request);

    // then
    assertEquals(
        List.of(303L, 302L),
        response.applications().stream()
            .map(item -> item.applicationId())
            .toList()
    );
    assertTrue(response.hasNext());
    assertNotNull(response.nextCursor());

    var firstItem = response.applications().getFirst();
    assertAll(
        () -> assertEquals(ApplicationStatus.APPLIED, firstItem.status()),
        () -> assertEquals(103L, firstItem.post().postId()),
        () -> assertEquals("공고103", firstItem.post().title()),
        () -> assertEquals(
            "https://example.com/103.jpg",
            firstItem.post().representativeImageUrl()
        ),
        () -> assertNull(
            response.applications().get(1).post().representativeImageUrl()
        ),
        () -> assertEquals(
            OffsetDateTime.parse("2026-11-19T10:20:00+09:00"),
            firstItem.createdAt()
        ),
        () -> assertEquals(
            OffsetDateTime.parse("2026-11-19T11:20:00+09:00"),
            firstItem.updatedAt()
        ),
        () -> assertNull(firstItem.approvedAt()),
        () -> assertNull(firstItem.completedAt()),
        () -> assertNull(firstItem.rejectedAt())
    );

    MyApplicationCursor next = cursorCodec.decode(response.nextCursor());
    assertAll(
        () -> assertEquals(MEMBER_ID, next.memberId()),
        () -> assertNull(next.status()),
        () -> assertEquals(CREATED_AT, next.createdAt()),
        () -> assertEquals(302L, next.applicationId())
    );

    verify(postImageRepository)
        .findRepresentativeImagesByPostIds(Set.of(103L, 102L));
  }

  @Test
  @DisplayName("유효한 커서로 조회한 결과 없음 -> 빈 목록과 다음 페이지 없음")
  void returnsEmptyNextPage() {
    // given
    Member applicant = createMember(MEMBER_ID);
    String cursor = cursorCodec.encode(new MyApplicationCursor(
        1, MEMBER_ID, ApplicationStatus.APPLIED, CREATED_AT, 302L
    ));
    MyApplicationListRequest request = new MyApplicationListRequest(ApplicationStatus.APPLIED, cursor, 2);

    when(memberRepository.findById(MEMBER_ID))
        .thenReturn(Optional.of(applicant));
    when(applicationRepository.findMyApplications(
        MEMBER_ID,
        ApplicationStatus.APPLIED,
        CREATED_AT,
        302L,
        PageRequest.of(0, 3)
    )).thenReturn(List.of());

    // when
    MyApplicationListResponse response = service.getMyApplications(MEMBER_ID, request);

    // then
    assertAll(
        () -> assertTrue(response.applications().isEmpty()),
        () -> assertNull(response.nextCursor()),
        () -> assertFalse(response.hasNext())
    );

    verify(applicationRepository).findMyApplications(
        MEMBER_ID,
        ApplicationStatus.APPLIED,
        CREATED_AT,
        302L,
        PageRequest.of(0, 3)
    );
    verifyNoInteractions(postImageRepository);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @DisplayName("커서의 회원 또는 상태 필터 불일치 -> 커서 오류 및 목록 조회 중단")
  void rejectsMismatchedCursor(boolean differentMember) {
    // given
    Member applicant = createMember(MEMBER_ID);

    Long cursorMemberId = differentMember ? 2L : MEMBER_ID;
    ApplicationStatus cursorStatus = differentMember ? ApplicationStatus.APPLIED : null;

    String cursor = cursorCodec.encode(new MyApplicationCursor(
        1, cursorMemberId, cursorStatus, CREATED_AT, 302L
    ));
    MyApplicationListRequest request = new MyApplicationListRequest(ApplicationStatus.APPLIED, cursor, 2);

    when(memberRepository.findById(MEMBER_ID))
        .thenReturn(Optional.of(applicant));

    // when & then
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.getMyApplications(MEMBER_ID, request)
    );

    assertEquals(ApplicationErrorCode.INVALID_CURSOR, exception.getErrorCode());
    verifyNoInteractions(applicationRepository, postImageRepository);
  }

  @Test
  @DisplayName("탈퇴한 회원 -> 인증 오류 및 목록 조회 중단")
  void rejectsWithdrawnMember() {
    // given
    Member member = createMember(MEMBER_ID);
    member.withdraw(CREATED_AT);
    MyApplicationListRequest request = new MyApplicationListRequest(null, null, 10);

    when(memberRepository.findById(MEMBER_ID))
        .thenReturn(Optional.of(member));

    // when & then
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.getMyApplications(MEMBER_ID, request)
    );

    assertEquals(CommonErrorCode.UNAUTHORIZED, exception.getErrorCode());
    verifyNoInteractions(applicationRepository, postImageRepository);
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

  private Application createApplication(
      Long applicationId,
      Long postId,
      Member applicant
  ) {
    Post post = new Post(
        createMember(2L),
        "공고" + postId,
        "공고 내용",
        NoticeType.ADOPTION,
        Species.DOG,
        "믹스",
        null,
        Gender.UNKNOWN,
        false,
        null,
        null,
        Region.SEOUL,
        null,
        null
    );
    ReflectionTestUtils.setField(post, "postId", postId);

    Application application = new Application(
        post,
        applicant,
        "회사원",
        HousingType.APARTMENT,
        HousingOwnership.OWNED,
        null,
        false,
        8,
        2,
        "30대 2명",
        true,
        false,
        "없음",
        null,
        true,
        true
    );
    ReflectionTestUtils.setField(application, "applicationId", applicationId);
    ReflectionTestUtils.setField(application, "createdAt", CREATED_AT);
    ReflectionTestUtils.setField(
        application, "updatedAt", CREATED_AT.plusHours(1)
    );
    return application;
  }

  private RepresentativeImageView imageView(Long postId, String imageUrl) {
    return new RepresentativeImageView() {
      @Override
      public Long getPostId() {
        return postId;
      }

      @Override
      public String getImageUrl() {
        return imageUrl;
      }
    };
  }
}