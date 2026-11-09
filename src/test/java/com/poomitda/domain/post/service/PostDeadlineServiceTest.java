package com.poomitda.domain.post.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.poomitda.domain.matching.entity.constant.ApplicationStatus;
import com.poomitda.domain.matching.repository.ApplicationRepository;
import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.post.entity.Post;
import com.poomitda.domain.post.entity.constant.*;
import com.poomitda.domain.post.exception.PostErrorCode;
import com.poomitda.domain.post.repository.PostRepository;
import com.poomitda.global.constant.*;
import com.poomitda.global.exception.exception.BusinessException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.util.ReflectionTestUtils;

class PostDeadlineServiceTest {

  private static final Long POST_ID = 100L;

  private static final List<ApplicationStatus> SELECTED_STATUSES =
      List.of(ApplicationStatus.APPROVED, ApplicationStatus.COMPLETED);
  private static final List<ApplicationStatus> PENDING_STATUSES =
      List.of(ApplicationStatus.APPLIED, ApplicationStatus.UNDER_REVIEW);

  private final PostRepository postRepository =
      mock(PostRepository.class);
  private final ApplicationRepository applicationRepository =
      mock(ApplicationRepository.class);

  private final PostDeadlineService postDeadlineService =
      new PostDeadlineService(postRepository, applicationRepository);

  @Test
  @DisplayName("조회 가능한 공고 없음 -> 공고 없음 예외 및 신청 조회 생략")
  void rejectsMissingPost() {
    // given
    when(postRepository.findByIdForUpdate(POST_ID))
        .thenReturn(Optional.empty());

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> postDeadlineService.updateDeadlineStatus(POST_ID)
    );

    // then
    assertEquals(PostErrorCode.POST_NOT_FOUND, exception.getErrorCode());
    verifyNoInteractions(applicationRepository);
  }

  @ParameterizedTest
  @EnumSource(PostClosedReason.class)
  @DisplayName("이미 종료된 공고 -> 기존 종료 사유와 시각 유지")
  void preservesClosedPost(PostClosedReason reason) {
    // given
    Post post = createPost(utcNow().minusDays(2));
    LocalDateTime originalClosedAt = utcNow().minusDays(1);

    if (reason == PostClosedReason.NO_APPLICANTS || reason == PostClosedReason.NO_SELECTION) {
      post.closeAfterDeadline(reason, originalClosedAt);
    } else {
      ReflectionTestUtils.setField(post, "status", PostStatus.CLOSED);
      ReflectionTestUtils.setField(post, "closedReason", reason);
      ReflectionTestUtils.setField(post, "closedAt", originalClosedAt);
    }

    preparePost(post);

    // when
    postDeadlineService.updateDeadlineStatus(POST_ID);

    // then
    assertAll(
        () -> assertEquals(PostStatus.CLOSED, post.getStatus()),
        () -> assertEquals(reason, post.getClosedReason()),
        () -> assertEquals(originalClosedAt, post.getClosedAt())
    );
    verifyNoInteractions(applicationRepository);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @DisplayName("마감 시각 없음 또는 마감 전 -> 모집 상태 유지 및 신청 조회 생략")
  void preservesPostBeforeDeadline(boolean withoutDeadline) {
    // given
    LocalDateTime deadline = withoutDeadline ? null : utcNow().plusDays(1);
    Post post = createPost(deadline);
    preparePost(post);

    // when
    postDeadlineService.updateDeadlineStatus(POST_ID);

    // then
    assertAll(
        () -> assertEquals(PostStatus.RECRUITING, post.getStatus()),
        () -> assertNull(post.getClosedReason()),
        () -> assertNull(post.getClosedAt())
    );
    verifyNoInteractions(applicationRepository);
  }

  @ParameterizedTest
  @EnumSource(
      value = PostStatus.class,
      names = {"RECRUITING", "RECEPTION_CLOSED"}
  )
  @DisplayName("마감된 미종료 공고에 선정된 신청 존재 -> 상태 불일치 예외")
  void rejectsSelectedApplicationOnOpenPost(PostStatus initialStatus) {
    // given
    Post post = createExpiredPost(initialStatus);
    preparePost(post);

    when(applicationRepository.existsByPost_PostIdAndStatusIn(POST_ID, SELECTED_STATUSES))
        .thenReturn(true);

    // when
    assertThrows(
        IllegalStateException.class,
        () -> postDeadlineService.updateDeadlineStatus(POST_ID)
    );

    // then
    assertAll(
        () -> assertEquals(initialStatus, post.getStatus()),
        () -> assertNull(post.getClosedReason()),
        () -> assertNull(post.getClosedAt())
    );

    verify(applicationRepository).existsByPost_PostIdAndStatusIn(POST_ID, SELECTED_STATUSES);
    verifyNoMoreInteractions(applicationRepository);
  }

  @ParameterizedTest
  @EnumSource(
      value = PostStatus.class,
      names = {"RECRUITING", "RECEPTION_CLOSED"}
  )
  @DisplayName("마감 후 심사할 신청 존재 -> 접수 종료 상태 및 종료 정보 없음")
  void closesReceptionWhenPendingApplicationExists(PostStatus initialStatus) {
    // given
    Post post = createExpiredPost(initialStatus);
    preparePost(post);

    when(applicationRepository.existsByPost_PostIdAndStatusIn(POST_ID, SELECTED_STATUSES))
        .thenReturn(false);

    when(applicationRepository.existsByPost_PostIdAndStatusIn(POST_ID, PENDING_STATUSES))
        .thenReturn(true);

    // when
    postDeadlineService.updateDeadlineStatus(POST_ID);

    // then
    assertAll(
        () -> assertEquals(PostStatus.RECEPTION_CLOSED, post.getStatus()),
        () -> assertNull(post.getClosedReason()),
        () -> assertNull(post.getClosedAt())
    );

    verify(applicationRepository).existsByPost_PostIdAndStatusIn(POST_ID, SELECTED_STATUSES);
    verify(applicationRepository).existsByPost_PostIdAndStatusIn(POST_ID, PENDING_STATUSES);
    verifyNoMoreInteractions(applicationRepository);
  }

  @ParameterizedTest
  @CsvSource({
      "RECRUITING,false,NO_APPLICANTS",
      "RECEPTION_CLOSED,false,NO_APPLICANTS",
      "RECRUITING,true,NO_SELECTION",
      "RECEPTION_CLOSED,true,NO_SELECTION"
  })
  @DisplayName("마감 후 선정 및 심사 대상 없음 -> 신청 이력에 따른 모집 종료")
  void closesPostWithReasonBasedOnHistory(
      PostStatus initialStatus,
      boolean hasApplicationHistory,
      PostClosedReason expectedReason
  ) {
    // given
    Post post = createExpiredPost(initialStatus);
    preparePost(post);

    when(applicationRepository.existsByPost_PostIdAndStatusIn(POST_ID, SELECTED_STATUSES))
        .thenReturn(false);

    when(applicationRepository.existsByPost_PostIdAndStatusIn(POST_ID, PENDING_STATUSES))
        .thenReturn(false);

    when(applicationRepository.existsByPost_PostId(POST_ID))
        .thenReturn(hasApplicationHistory);

    // when
    LocalDateTime before = utcNow();
    postDeadlineService.updateDeadlineStatus(POST_ID);
    LocalDateTime after = utcNow();

    // then
    assertAll(
        () -> assertEquals(PostStatus.CLOSED, post.getStatus()),
        () -> assertEquals(expectedReason, post.getClosedReason()),
        () -> assertNotNull(post.getClosedAt())
    );

    assertFalse(post.getClosedAt().isBefore(before));
    assertFalse(post.getClosedAt().isAfter(after));

    verify(applicationRepository).existsByPost_PostIdAndStatusIn(POST_ID, SELECTED_STATUSES);
    verify(applicationRepository).existsByPost_PostIdAndStatusIn(POST_ID, PENDING_STATUSES);
    verify(applicationRepository).existsByPost_PostId(POST_ID);
    verifyNoMoreInteractions(applicationRepository);
  }

  private Post createPost(LocalDateTime deadline) {
    Post post = new Post(
        mock(Member.class),
        "마감 처리 테스트 공고",
        "공고 마감 상태를 검증합니다.",
        NoticeType.ADOPTION,
        Species.CAT,
        "코리안 숏헤어",
        null,
        Gender.UNKNOWN,
        false,
        null,
        null,
        Region.GYEONGGI,
        "구리시",
        deadline
    );
    ReflectionTestUtils.setField(post, "postId", POST_ID);
    return post;
  }

  private Post createExpiredPost(PostStatus initialStatus) {
    Post post = createPost(utcNow().minusDays(1));
    if (initialStatus == PostStatus.RECEPTION_CLOSED) post.closeReception();
    return post;
  }

  private void preparePost(Post post) {
    when(postRepository.findByIdForUpdate(POST_ID))
        .thenReturn(Optional.of(post));
  }

  private LocalDateTime utcNow() {
    return LocalDateTime.now(ZoneOffset.UTC);
  }
}