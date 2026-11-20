package com.poomitda.domain.matching.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.poomitda.domain.matching.cursor.*;
import com.poomitda.domain.matching.dto.ApplicationRequestDto.PostApplicationListRequest;
import com.poomitda.domain.matching.dto.ApplicationResponseDto.PostApplicationListItemResponse;
import com.poomitda.domain.matching.entity.Application;
import com.poomitda.domain.matching.entity.constant.*;
import com.poomitda.domain.matching.exception.ApplicationErrorCode;
import com.poomitda.domain.matching.repository.ApplicationRepository;
import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.domain.post.entity.Post;
import com.poomitda.domain.post.entity.constant.*;
import com.poomitda.domain.post.exception.PostErrorCode;
import com.poomitda.domain.post.repository.PostRepository;
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

class PostApplicationListServiceTest {

  private static final Long MEMBER_ID = 2L;
  private static final Long POST_ID = 100L;
  private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 20, 1, 20);

  private final MemberRepository memberRepository = mock(MemberRepository.class);
  private final PostRepository postRepository = mock(PostRepository.class);
  private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);

  private final PostApplicationCursorCodec cursorCodec = new PostApplicationCursorCodec(JsonMapper.builder().build());

  private final PostApplicationListService service =
      new PostApplicationListService(
          memberRepository,
          postRepository,
          applicationRepository,
          cursorCodec,
          new PostApplicationCursorValidator(),
          new PostApplicationCursorFactory()
      );

  @Test
  @DisplayName("조회 개수보다 많은 신청 -> 신청자 정보와 마지막 반환 신청 기준 커서")
  void returnsFirstPageWithCursor() {
    // given
    Member author = createMember(MEMBER_ID);
    Post post = createPost(author);

    Application first = createApplication(303L, post, createMember(11L));
    Application second = createApplication(302L, post, createMember(12L));
    Application extra = createApplication(301L, post, createMember(13L));

    PostApplicationListRequest request = new PostApplicationListRequest(POST_ID, null, null, 2);

    when(memberRepository.findById(MEMBER_ID))
        .thenReturn(Optional.of(author));
    when(postRepository.findByPostId(POST_ID))
        .thenReturn(Optional.of(post));
    when(applicationRepository.findPostApplications(
        POST_ID, null, null, null, PageRequest.of(0, 3)
    )).thenReturn(List.of(first, second, extra));

    // when
    var response = service.getPostApplications(MEMBER_ID, request);

    // then
    assertEquals(
        List.of(303L, 302L),
        response.applications().stream()
            .map(PostApplicationListItemResponse::applicationId)
            .toList()
    );
    assertTrue(response.hasNext());
    assertNotNull(response.nextCursor());

    var firstItem = response.applications().getFirst();
    assertAll(
        () -> assertEquals(ApplicationStatus.APPLIED, firstItem.status()),
        () -> assertEquals(11L, firstItem.applicant().memberId()),
        () -> assertEquals("회원11", firstItem.applicant().nickname()),
        () -> assertEquals(
            OffsetDateTime.parse("2026-11-20T10:20:00+09:00"),
            firstItem.createdAt()
        ),
        () -> assertEquals(
            OffsetDateTime.parse("2026-11-20T11:20:00+09:00"),
            firstItem.updatedAt()
        ),
        () -> assertNull(firstItem.approvedAt()),
        () -> assertNull(firstItem.completedAt()),
        () -> assertNull(firstItem.completionMethod()),
        () -> assertNull(firstItem.rejectionReason()),
        () -> assertNull(firstItem.rejectedAt())
    );

    PostApplicationCursor next = cursorCodec.decode(response.nextCursor());
    assertAll(
        () -> assertEquals(Integer.valueOf(1), next.version()),
        () -> assertEquals(MEMBER_ID, next.memberId()),
        () -> assertEquals(POST_ID, next.postId()),
        () -> assertNull(next.status()),
        () -> assertEquals(CREATED_AT, next.createdAt()),
        () -> assertEquals(302L, next.applicationId())
    );

    verify(applicationRepository).findPostApplications(
        POST_ID, null, null, null, PageRequest.of(0, 3)
    );
  }

  @Test
  @DisplayName("유효한 다음 커서로 조회한 결과 없음 -> 빈 목록과 다음 페이지 없음")
  void returnsEmptyNextPage() {
    // given
    Member author = createMember(MEMBER_ID);
    Post post = createPost(author);

    String cursor = cursorCodec.encode(new PostApplicationCursor(
        1, MEMBER_ID, POST_ID, ApplicationStatus.APPLIED, CREATED_AT, 302L
    ));
    PostApplicationListRequest request = new PostApplicationListRequest(
        POST_ID, ApplicationStatus.APPLIED, cursor, 2
    );

    when(memberRepository.findById(MEMBER_ID))
        .thenReturn(Optional.of(author));
    when(postRepository.findByPostId(POST_ID))
        .thenReturn(Optional.of(post));
    when(applicationRepository.findPostApplications(
        POST_ID,
        ApplicationStatus.APPLIED,
        CREATED_AT,
        302L,
        PageRequest.of(0, 3)
    )).thenReturn(List.of());

    // when
    var response = service.getPostApplications(MEMBER_ID, request);

    // then
    assertAll(
        () -> assertTrue(response.applications().isEmpty()),
        () -> assertNull(response.nextCursor()),
        () -> assertFalse(response.hasNext())
    );

    verify(applicationRepository).findPostApplications(
        POST_ID,
        ApplicationStatus.APPLIED,
        CREATED_AT,
        302L,
        PageRequest.of(0, 3)
    );
  }

  @Test
  @DisplayName("다른 회원이 작성한 공고 -> 권한 오류 및 신청 목록 조회 중단")
  void rejectsNonAuthor() {
    // given
    Member currentMember = createMember(MEMBER_ID);
    Post post = createPost(createMember(99L));
    PostApplicationListRequest request = new PostApplicationListRequest(POST_ID, null, null, 10);

    when(memberRepository.findById(MEMBER_ID))
        .thenReturn(Optional.of(currentMember));
    when(postRepository.findByPostId(POST_ID))
        .thenReturn(Optional.of(post));

    // when & then
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.getPostApplications(MEMBER_ID, request)
    );

    assertEquals(CommonErrorCode.FORBIDDEN, exception.getErrorCode());
    verifyNoInteractions(applicationRepository);
  }

  @Test
  @DisplayName("존재하지 않는 공고 -> 공고 없음 오류 및 신청 목록 조회 중단")
  void rejectsMissingPost() {
    // given
    Member author = createMember(MEMBER_ID);
    PostApplicationListRequest request =
        new PostApplicationListRequest(POST_ID, null, null, 10);

    when(memberRepository.findById(MEMBER_ID))
        .thenReturn(Optional.of(author));
    when(postRepository.findByPostId(POST_ID))
        .thenReturn(Optional.empty());

    // when & then
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.getPostApplications(MEMBER_ID, request)
    );

    assertEquals(PostErrorCode.POST_NOT_FOUND, exception.getErrorCode());
    verifyNoInteractions(applicationRepository);
  }

  @ParameterizedTest
  @ValueSource(strings = {"member", "post", "status"})
  @DisplayName("커서의 회원 또는 공고 또는 상태 불일치 -> 커서 오류 및 목록 조회 중단")
  void rejectsMismatchedCursor(String mismatch) {
    // given
    Member author = createMember(MEMBER_ID);
    Post post = createPost(author);

    Long cursorMemberId = mismatch.equals("member") ? 99L : MEMBER_ID;
    Long cursorPostId = mismatch.equals("post") ? 999L : POST_ID;
    ApplicationStatus cursorStatus = mismatch.equals("status")
        ? null
        : ApplicationStatus.APPLIED;

    String cursor = cursorCodec.encode(new PostApplicationCursor(
        1,
        cursorMemberId,
        cursorPostId,
        cursorStatus,
        CREATED_AT,
        302L
    ));
    PostApplicationListRequest request = new PostApplicationListRequest(
        POST_ID, ApplicationStatus.APPLIED, cursor, 2
    );

    when(memberRepository.findById(MEMBER_ID))
        .thenReturn(Optional.of(author));
    when(postRepository.findByPostId(POST_ID))
        .thenReturn(Optional.of(post));

    // when & then
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.getPostApplications(MEMBER_ID, request)
    );

    assertEquals(ApplicationErrorCode.INVALID_CURSOR, exception.getErrorCode());
    verifyNoInteractions(applicationRepository);
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

  private Post createPost(Member author) {
    Post post = new Post(
        author,
        "신청 목록 검증 공고",
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
    ReflectionTestUtils.setField(post, "postId", POST_ID);
    return post;
  }

  private Application createApplication(
      Long applicationId,
      Post post,
      Member applicant
  ) {
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
}