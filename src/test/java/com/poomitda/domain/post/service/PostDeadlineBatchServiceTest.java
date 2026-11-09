package com.poomitda.domain.post.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.poomitda.domain.post.entity.constant.PostStatus;
import com.poomitda.domain.post.exception.PostErrorCode;
import com.poomitda.domain.post.repository.PostRepository;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.springframework.data.domain.*;

class PostDeadlineBatchServiceTest {

  private static final List<PostStatus> TARGET_STATUSES = List.of(
      PostStatus.RECRUITING,
      PostStatus.RECEPTION_CLOSED
  );

  private static final Pageable PAGEABLE = PageRequest.of(0, 100);

  private final PostRepository postRepository =
      mock(PostRepository.class);

  private final PostDeadlineService postDeadlineService =
      mock(PostDeadlineService.class);

  private final PostDeadlineBatchService batchService = new PostDeadlineBatchService(
      postRepository,
      postDeadlineService
  );

  @Test
  @DisplayName("처리 대상 없음 -> 한 번 조회 후 종료")
  void stopsWhenNoTargetsExist() {
    // given
    when(postRepository.findDeadlineTargetIds(
        eq(TARGET_STATUSES),
        any(LocalDateTime.class),
        eq(0L),
        eq(PAGEABLE)
    )).thenReturn(List.of());

    // when
    batchService.updateExpiredPosts();

    // then
    verify(postRepository).findDeadlineTargetIds(
        eq(TARGET_STATUSES),
        any(LocalDateTime.class),
        eq(0L),
        eq(PAGEABLE)
    );
    verifyNoMoreInteractions(postRepository);
    verifyNoInteractions(postDeadlineService);
  }

  @Test
  @DisplayName("여러 묶음 존재 -> 기준 시각을 유지하며 마지막 ID 이후 순회")
  void processesMultipleBatches() {
    // given
    prepareBatch(0L, List.of(10L, 20L));
    prepareBatch(20L, List.of(30L));
    prepareBatch(30L, List.of());

    // when
    batchService.updateExpiredPosts();

    // then
    ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

    InOrder order = inOrder(postRepository, postDeadlineService);

    order.verify(postRepository).findDeadlineTargetIds(
        eq(TARGET_STATUSES),
        timeCaptor.capture(),
        eq(0L),
        eq(PAGEABLE)
    );
    order.verify(postDeadlineService).updateDeadlineStatus(10L);
    order.verify(postDeadlineService).updateDeadlineStatus(20L);

    order.verify(postRepository).findDeadlineTargetIds(
        eq(TARGET_STATUSES),
        timeCaptor.capture(),
        eq(20L),
        eq(PAGEABLE)
    );
    order.verify(postDeadlineService).updateDeadlineStatus(30L);

    order.verify(postRepository).findDeadlineTargetIds(
        eq(TARGET_STATUSES),
        timeCaptor.capture(),
        eq(30L),
        eq(PAGEABLE)
    );
    order.verifyNoMoreInteractions();

    List<LocalDateTime> times = timeCaptor.getAllValues();

    assertEquals(3, times.size());
    assertTrue(times.stream().allMatch(times.getFirst()::equals));
  }

  @ParameterizedTest
  @MethodSource("individualFailures")
  @DisplayName("묶음 마지막 공고 처리 실패 -> 다음 묶음 처리 계속")
  void continuesAfterIndividualFailure(RuntimeException failure) {
    // given
    prepareBatch(0L, List.of(10L, 20L));
    prepareBatch(20L, List.of(30L));
    prepareBatch(30L, List.of());

    doThrow(failure)
        .when(postDeadlineService)
        .updateDeadlineStatus(20L);

    // when & then
    assertDoesNotThrow(batchService::updateExpiredPosts);

    InOrder order = inOrder(postRepository, postDeadlineService);

    order.verify(postRepository).findDeadlineTargetIds(
        eq(TARGET_STATUSES),
        any(LocalDateTime.class),
        eq(0L),
        eq(PAGEABLE)
    );
    order.verify(postDeadlineService).updateDeadlineStatus(10L);
    order.verify(postDeadlineService).updateDeadlineStatus(20L);

    order.verify(postRepository).findDeadlineTargetIds(
        eq(TARGET_STATUSES),
        any(LocalDateTime.class),
        eq(20L),
        eq(PAGEABLE)
    );
    order.verify(postDeadlineService).updateDeadlineStatus(30L);

    order.verify(postRepository).findDeadlineTargetIds(
        eq(TARGET_STATUSES),
        any(LocalDateTime.class),
        eq(30L),
        eq(PAGEABLE)
    );
    order.verifyNoMoreInteractions();
  }

  @Test
  @DisplayName("다음 묶음 조회 실패 -> 예외 전달 및 현재 실행 중단")
  void stopsWhenTargetQueryFails() {
    // given
    prepareBatch(0L, List.of(10L));

    RuntimeException failure = new IllegalStateException("목록 조회 실패");

    when(postRepository.findDeadlineTargetIds(
        eq(TARGET_STATUSES),
        any(LocalDateTime.class),
        eq(10L),
        eq(PAGEABLE)
    )).thenThrow(failure);

    // when
    RuntimeException thrown = assertThrows(
        RuntimeException.class,
        () -> batchService.updateExpiredPosts()
    );

    // then
    assertSame(failure, thrown);

    InOrder order = inOrder(postRepository, postDeadlineService);

    order.verify(postRepository).findDeadlineTargetIds(
        eq(TARGET_STATUSES),
        any(LocalDateTime.class),
        eq(0L),
        eq(PAGEABLE)
    );
    order.verify(postDeadlineService).updateDeadlineStatus(10L);
    order.verify(postRepository).findDeadlineTargetIds(
        eq(TARGET_STATUSES),
        any(LocalDateTime.class),
        eq(10L),
        eq(PAGEABLE)
    );
    order.verifyNoMoreInteractions();
  }

  @Test
  @DisplayName("이전 실행에서 개별 처리 실패 -> 다음 실행은 0부터 조회하여 재시도")
  void retriesFailedPostOnNextRun() {
    // given
    prepareBatch(0L, List.of(10L));
    prepareBatch(10L, List.of());

    doThrow(new IllegalStateException("첫 처리 실패"))
        .doNothing()
        .when(postDeadlineService)
        .updateDeadlineStatus(10L);

    // when
    batchService.updateExpiredPosts();
    batchService.updateExpiredPosts();

    // then
    InOrder order = inOrder(postRepository, postDeadlineService);

    for (int run = 0; run < 2; run++) {
      order.verify(postRepository).findDeadlineTargetIds(
          eq(TARGET_STATUSES),
          any(LocalDateTime.class),
          eq(0L),
          eq(PAGEABLE)
      );
      order.verify(postDeadlineService).updateDeadlineStatus(10L);
      order.verify(postRepository).findDeadlineTargetIds(
          eq(TARGET_STATUSES),
          any(LocalDateTime.class),
          eq(10L),
          eq(PAGEABLE)
      );
    }

    order.verifyNoMoreInteractions();
  }

  private static Stream<Arguments> individualFailures() {
    return Stream.of(
        Arguments.of(new BusinessException(PostErrorCode.POST_NOT_FOUND)),
        Arguments.of(new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE)),
        Arguments.of(new IllegalStateException("공고와 신청 상태 불일치"))
    );
  }

  private void prepareBatch(Long lastPostId, List<Long> postIds) {
    when(postRepository.findDeadlineTargetIds(
        eq(TARGET_STATUSES),
        any(LocalDateTime.class),
        eq(lastPostId),
        eq(PAGEABLE)
    )).thenReturn(postIds);
  }
}