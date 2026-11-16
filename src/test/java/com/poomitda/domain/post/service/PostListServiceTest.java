package com.poomitda.domain.post.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.poomitda.domain.post.cursor.*;
import com.poomitda.domain.post.dto.PostRequestDto.PostListRequest;
import com.poomitda.domain.post.dto.PostResponseDto.PostListResponse;
import com.poomitda.domain.post.exception.PostErrorCode;
import com.poomitda.domain.post.validation.PostListValidator;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;

class PostListServiceTest {

  private final PostListValidator postListValidator = mock(PostListValidator.class);

  private final PostCursorCodec postCursorCodec = mock(PostCursorCodec.class);

  private final PostCursorValidator postCursorValidator = mock(PostCursorValidator.class);

  private final PostListDeadlineService postListDeadlineService = mock(PostListDeadlineService.class);

  private final PostService postService = mock(PostService.class);

  private final PostListService postListService = new PostListService(
      postListValidator,
      postCursorCodec,
      postCursorValidator,
      postListDeadlineService,
      postService
  );

  @Test
  @DisplayName("검색 요청이 null -> 예외 발생 및 후속 처리 없음")
  void rejectsNullRequest() {
    // given
    PostListRequest request = null;

    // when & then
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> postListService.getPostList(request)
    );

    assertEquals(
        "공고 목록 조회 요청은 null일 수 없습니다.",
        exception.getMessage()
    );

    verifyNoInteractions(
        postListValidator,
        postCursorCodec,
        postCursorValidator,
        postListDeadlineService,
        postService
    );
  }

  @Test
  @DisplayName("커서 없는 첫 페이지 -> UTC 기준일로 마감 갱신 후 목록 조회")
  void getsFirstPageAfterUpdatingDeadlines() {
    // given
    PostListRequest request = createRequest(null);
    PostListResponse expected = new PostListResponse(
        List.of(), null, false
    );

    when(postService.getPostList(
        eq(request),
        isNull(),
        any(LocalDate.class),
        any(LocalDateTime.class)
    )).thenReturn(expected);

    ArgumentCaptor<LocalDate> dateCaptor = ArgumentCaptor.forClass(LocalDate.class);
    ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

    LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);

    // when
    PostListResponse actual = postListService.getPostList(request);
    LocalDateTime after = LocalDateTime.now(ZoneOffset.UTC);

    // then
    assertSame(expected, actual);

    InOrder order = inOrder(
        postListValidator,
        postListDeadlineService,
        postService
    );

    order.verify(postListValidator).validate(request);
    order.verify(postListDeadlineService).updateExpiredPostsForSearch(
        eq(request),
        dateCaptor.capture(),
        timeCaptor.capture()
    );

    LocalDate referenceDate = dateCaptor.getValue();
    LocalDateTime now = timeCaptor.getValue();

    assertEquals(now.toLocalDate(), referenceDate);
    assertFalse(now.isBefore(before));
    assertFalse(now.isAfter(after));

    order.verify(postService).getPostList(
        request, null, referenceDate, now
    );

    order.verifyNoMoreInteractions();
    verifyNoInteractions(postCursorCodec, postCursorValidator);
  }

  @Test
  @DisplayName("유효한 다음 페이지 커서 -> 검증 후 기존 나이 기준일 유지")
  void preservesCursorReferenceDate() {
    // given
    PostListRequest request = createRequest("next-cursor");
    LocalDate originalDate = LocalDate.of(2026, 1, 15);
    PostCursor cursor = createCursor(request, originalDate);
    PostListResponse expected = new PostListResponse(
        List.of(), null, false
    );

    when(postCursorCodec.decode("next-cursor")).thenReturn(cursor);
    when(postService.getPostList(
        eq(request),
        eq(cursor),
        eq(originalDate),
        any(LocalDateTime.class)
    )).thenReturn(expected);

    ArgumentCaptor<LocalDateTime> timeCaptor =
        ArgumentCaptor.forClass(LocalDateTime.class);

    // when
    PostListResponse actual = postListService.getPostList(request);

    // then
    assertSame(expected, actual);

    InOrder order = inOrder(
        postListValidator,
        postCursorCodec,
        postCursorValidator,
        postListDeadlineService,
        postService
    );

    order.verify(postListValidator).validate(request);
    order.verify(postCursorCodec).decode("next-cursor");
    order.verify(postCursorValidator).validate(cursor, request);
    order.verify(postListDeadlineService).updateExpiredPostsForSearch(
        eq(request),
        eq(originalDate),
        timeCaptor.capture()
    );
    order.verify(postService).getPostList(
        request, cursor, originalDate, timeCaptor.getValue()
    );

    order.verifyNoMoreInteractions();
  }

  @Test
  @DisplayName("검색 조건 조합 검증 실패 -> 커서 처리와 DB 관련 호출 중단")
  void stopsWhenRequestValidationFails() {
    // given
    PostListRequest request = createRequest("next-cursor");
    BusinessException expected = new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);

    doThrow(expected).when(postListValidator).validate(request);

    // when
    BusinessException actual = assertThrows(
        BusinessException.class,
        () -> postListService.getPostList(request)
    );

    // then
    assertSame(expected, actual);
    verify(postListValidator).validate(request);

    verifyNoInteractions(
        postCursorCodec,
        postCursorValidator,
        postListDeadlineService,
        postService
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "invalid-cursor"})
  @DisplayName("전달된 커서 복원 실패 -> 첫 페이지로 처리하지 않고 중단")
  void stopsWhenCursorDecodingFails(String encodedCursor) {
    // given
    PostListRequest request = createRequest(encodedCursor);
    BusinessException expected = new BusinessException(PostErrorCode.INVALID_CURSOR);

    when(postCursorCodec.decode(encodedCursor)).thenThrow(expected);

    // when
    BusinessException actual = assertThrows(
        BusinessException.class,
        () -> postListService.getPostList(request)
    );

    // then
    assertSame(expected, actual);

    InOrder order = inOrder(
        postListValidator,
        postCursorCodec
    );
    order.verify(postListValidator).validate(request);
    order.verify(postCursorCodec).decode(encodedCursor);

    verifyNoInteractions(
        postCursorValidator,
        postListDeadlineService,
        postService
    );
  }

  @Test
  @DisplayName("복원된 커서 검증 실패 -> 마감 갱신과 목록 조회 중단")
  void stopsWhenCursorValidationFails() {
    // given
    PostListRequest request = createRequest("next-cursor");
    PostCursor cursor = createCursor(
        request, LocalDate.of(2026, 1, 15)
    );
    BusinessException expected = new BusinessException(PostErrorCode.INVALID_CURSOR);

    when(postCursorCodec.decode("next-cursor")).thenReturn(cursor);
    doThrow(expected).when(postCursorValidator).validate(cursor, request);

    // when
    BusinessException actual = assertThrows(
        BusinessException.class,
        () -> postListService.getPostList(request)
    );

    // then
    assertSame(expected, actual);

    InOrder order = inOrder(
        postListValidator,
        postCursorCodec,
        postCursorValidator
    );
    order.verify(postListValidator).validate(request);
    order.verify(postCursorCodec).decode("next-cursor");
    order.verify(postCursorValidator).validate(cursor, request);

    verifyNoInteractions(postListDeadlineService, postService);
  }

  @Test
  @DisplayName("마감 상태 갱신 실패 -> 목록을 조회하지 않고 예외 전달")
  void stopsWhenDeadlineUpdateFails() {
    // given
    PostListRequest request = createRequest(null);
    IllegalStateException expected = new IllegalStateException(
        "종료되지 않은 공고에 승인 또는 완료된 신청이 존재합니다."
    );

    doThrow(expected).when(postListDeadlineService)
        .updateExpiredPostsForSearch(
            eq(request),
            any(LocalDate.class),
            any(LocalDateTime.class)
        );

    // when
    IllegalStateException actual = assertThrows(
        IllegalStateException.class,
        () -> postListService.getPostList(request)
    );

    // then
    assertSame(expected, actual);

    InOrder order = inOrder(
        postListValidator,
        postListDeadlineService
    );
    order.verify(postListValidator).validate(request);
    order.verify(postListDeadlineService).updateExpiredPostsForSearch(
        eq(request),
        any(LocalDate.class),
        any(LocalDateTime.class)
    );

    verifyNoInteractions(postCursorCodec, postCursorValidator, postService);
  }

  private PostListRequest createRequest(String cursor) {
    return new PostListRequest(
        null, // noticeType
        null, // species
        null, // gender
        null, // region
        null, // district
        null, // status: 기본값 적용
        null, // sort: 기본값 적용
        cursor,
        10,
        null, // minAgeMonths
        null, // maxAgeMonths
        null  // keyword
    );
  }

  private PostCursor createCursor(
      PostListRequest request,
      LocalDate referenceDate
  ) {
    PostCursorFilter filters = new PostCursorFilter(
        request.noticeType(),
        request.species(),
        request.gender(),
        request.region(),
        request.district(),
        request.status(),
        request.minAgeMonths(),
        request.maxAgeMonths(),
        request.keyword()
    );

    return new PostCursor(
        1,
        request.sort(),
        100L,
        referenceDate.minusDays(1).atStartOfDay(),
        null,
        null,
        filters,
        referenceDate
    );
  }
}