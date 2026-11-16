package com.poomitda.domain.post.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.poomitda.domain.post.dto.PostRequestDto.PostListRequest;
import com.poomitda.domain.post.exception.PostErrorCode;
import com.poomitda.domain.post.repository.PostRepository;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

class PostListDeadlineServiceTest {

  private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 11, 16);

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 10, 0);

  private final PostRepository postRepository = mock(PostRepository.class);

  private final PostDeadlineService postDeadlineService = mock(PostDeadlineService.class);

  private final PostListDeadlineService service =
      new PostListDeadlineService(
          postRepository,
          postDeadlineService
      );

  @ParameterizedTest
  @ValueSource(strings = {"request", "referenceDate", "now"})
  @DisplayName("필수 인자 누락 -> 예외 발생 및 DB 관련 호출 없음")
  void rejectsMissingArgument(String missingArgument) {
    // given
    PostListRequest request = missingArgument.equals("request")
        ? null : createRequest();
    LocalDate referenceDate = missingArgument.equals("referenceDate")
        ? null : REFERENCE_DATE;
    LocalDateTime now = missingArgument.equals("now")
        ? null : NOW;

    // when & then
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> service.updateExpiredPostsForSearch(
            request, referenceDate, now
        )
    );

    assertEquals(
        "목록 마감 처리에는 검색 요청, 나이 기준일, 마감 기준 시각이 필요합니다.",
        exception.getMessage()
    );

    verifyNoInteractions(postRepository, postDeadlineService);
  }

  @Test
  @DisplayName("마감 대상 없음 -> 개별 공고 처리 없이 종료")
  void finishesWhenNoTargetsExist() {
    // given
    PostListRequest request = createRequest();

    when(postRepository.findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 0L, 100
    )).thenReturn(List.of());

    // when
    service.updateExpiredPostsForSearch(request, REFERENCE_DATE, NOW);

    // then
    verify(postRepository).findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 0L, 100
    );
    verifyNoMoreInteractions(postRepository);
    verifyNoInteractions(postDeadlineService);
  }

  @Test
  @DisplayName("여러 묶음 존재 -> 기준값을 유지하며 마지막 ID 이후부터 순회")
  void processesMultipleBatches() {
    // given
    PostListRequest request = createRequest();

    when(postRepository.findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 0L, 100
    )).thenReturn(List.of(10L, 20L));

    when(postRepository.findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 20L, 100
    )).thenReturn(List.of(35L));

    when(postRepository.findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 35L, 100
    )).thenReturn(List.of());

    // when
    service.updateExpiredPostsForSearch(request, REFERENCE_DATE, NOW);

    // then
    InOrder order = inOrder(postRepository, postDeadlineService);

    order.verify(postRepository).findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 0L, 100
    );
    order.verify(postDeadlineService).updateDeadlineStatus(10L);
    order.verify(postDeadlineService).updateDeadlineStatus(20L);

    order.verify(postRepository).findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 20L, 100
    );
    order.verify(postDeadlineService).updateDeadlineStatus(35L);

    order.verify(postRepository).findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 35L, 100
    );

    order.verifyNoMoreInteractions();
  }

  @Test
  @DisplayName("대상 공고가 사라짐 -> 해당 공고를 건너뛰고 다음 묶음 계속 처리")
  void skipsMissingPostAndContinues() {
    // given
    PostListRequest request = createRequest();

    when(postRepository.findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 0L, 100
    )).thenReturn(List.of(10L, 20L));

    when(postRepository.findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 20L, 100
    )).thenReturn(List.of(30L));

    when(postRepository.findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 30L, 100
    )).thenReturn(List.of());

    doThrow(new BusinessException(PostErrorCode.POST_NOT_FOUND))
        .when(postDeadlineService).updateDeadlineStatus(20L);

    // when
    service.updateExpiredPostsForSearch(request, REFERENCE_DATE, NOW);

    // then
    InOrder order = inOrder(postRepository, postDeadlineService);

    order.verify(postRepository).findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 0L, 100
    );
    order.verify(postDeadlineService).updateDeadlineStatus(10L);
    order.verify(postDeadlineService).updateDeadlineStatus(20L);

    order.verify(postRepository).findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 20L, 100
    );
    order.verify(postDeadlineService).updateDeadlineStatus(30L);

    order.verify(postRepository).findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 30L, 100
    );

    order.verifyNoMoreInteractions();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @DisplayName("공고 없음 이외의 개별 처리 실패 -> 나머지 처리 중단 및 예외 전달")
  void stopsOnOtherProcessingFailure(boolean businessFailure) {
    // given
    PostListRequest request = createRequest();

    RuntimeException expected = businessFailure
        ? new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR)
        : new IllegalStateException(
            "종료되지 않은 공고에 승인 또는 완료된 신청이 존재합니다."
        );

    when(postRepository.findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 0L, 100
    )).thenReturn(List.of(10L, 20L, 30L));

    doThrow(expected)
        .when(postDeadlineService).updateDeadlineStatus(20L);

    // when
    RuntimeException actual = assertThrows(
        RuntimeException.class,
        () -> service.updateExpiredPostsForSearch(
            request, REFERENCE_DATE, NOW
        )
    );

    // then
    assertSame(expected, actual);

    InOrder order = inOrder(postRepository, postDeadlineService);
    order.verify(postRepository).findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 0L, 100
    );
    order.verify(postDeadlineService).updateDeadlineStatus(10L);
    order.verify(postDeadlineService).updateDeadlineStatus(20L);

    verify(postDeadlineService, never()).updateDeadlineStatus(30L);
    verifyNoMoreInteractions(postRepository, postDeadlineService);
  }

  @Test
  @DisplayName("대상 목록 조회 실패 -> 개별 처리 없이 예외 전달")
  void propagatesTargetQueryFailure() {
    // given
    PostListRequest request = createRequest();
    IllegalStateException expected = new IllegalStateException(
        "테스트용 마감 대상 조회 실패"
    );

    when(postRepository.findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 0L, 100
    )).thenThrow(expected);

    // when
    IllegalStateException actual = assertThrows(
        IllegalStateException.class,
        () -> service.updateExpiredPostsForSearch(
            request, REFERENCE_DATE, NOW
        )
    );

    // then
    assertSame(expected, actual);
    verify(postRepository).findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 0L, 100
    );
    verifyNoMoreInteractions(postRepository);
    verifyNoInteractions(postDeadlineService);
  }

  @Test
  @DisplayName("이전 실행 실패 후 다시 실행 -> 최초 ID부터 재조회")
  void restartsFromZeroAfterFailure() {
    // given
    PostListRequest request = createRequest();
    IllegalStateException expected = new IllegalStateException(
        "테스트용 일시적 마감 처리 실패"
    );

    when(postRepository.findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 0L, 100
    )).thenReturn(List.of(10L));

    when(postRepository.findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 10L, 100
    )).thenReturn(List.of());

    doThrow(expected)
        .doNothing()
        .when(postDeadlineService).updateDeadlineStatus(10L);

    // when
    IllegalStateException actual = assertThrows(
        IllegalStateException.class,
        () -> service.updateExpiredPostsForSearch(
            request, REFERENCE_DATE, NOW
        )
    );

    service.updateExpiredPostsForSearch(request, REFERENCE_DATE, NOW);

    // then
    assertSame(expected, actual);

    InOrder order = inOrder(postRepository, postDeadlineService);

    order.verify(postRepository).findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 0L, 100
    );
    order.verify(postDeadlineService).updateDeadlineStatus(10L);

    order.verify(postRepository).findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 0L, 100
    );
    order.verify(postDeadlineService).updateDeadlineStatus(10L);

    order.verify(postRepository).findDeadlineTargetIdsForSearch(
        request, REFERENCE_DATE, NOW, 10L, 100
    );

    order.verifyNoMoreInteractions();
  }

  private PostListRequest createRequest() {
    return new PostListRequest(
        null, // noticeType
        null, // species
        null, // gender
        null, // region
        null, // district
        null, // status: 기본값 적용
        null, // sort: 기본값 적용
        null, // cursor
        10,
        null, // minAgeMonths
        null, // maxAgeMonths
        null  // keyword
    );
  }
}