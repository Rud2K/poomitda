package com.poomitda.domain.post.service;

import com.poomitda.domain.post.entity.constant.PostStatus;
import com.poomitda.domain.post.exception.PostErrorCode;
import com.poomitda.domain.post.repository.PostRepository;
import com.poomitda.global.exception.exception.BusinessException;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostDeadlineBatchService {

  private static final int BATCH_SIZE = 100;

  private static final List<PostStatus> TARGET_STATUSES = List.of(
      PostStatus.RECRUITING,
      PostStatus.RECEPTION_CLOSED
  );

  private final PostRepository postRepository;
  private final PostDeadlineService postDeadlineService;

  /**
   * 실행 시작 시각을 기준으로 마감 처리 대상 공고를 순회한다.
   * 공고별 처리는 별도 트랜잭션에서 수행하며, 개별 처리 실패 후에도 순회를 계속한다.
   */
  public void updateExpiredPosts() {
    // LocalDateTime now에 UTC 현재 시각 저장
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

    // 최초 조회의 기준 ID를 0L로 초기화
    Long lastPostId = 0L;

    // Pageable pageable에 PageRequest.of(0, BATCH_SIZE) 저장
    Pageable pageable = PageRequest.of(0, BATCH_SIZE);

    while (true) {
      // List<Long> postIds에 대상 조회 결과 저장
      List<Long> postIds = postRepository.findDeadlineTargetIds(
          TARGET_STATUSES,
          now,
          lastPostId,
          pageable
      );

      // postIds.isEmpty()이면 return
      if (postIds.isEmpty()) return;

      // 각 Long postId를 순회
      for (Long postId : postIds) {
        // processPost(postId) 호출
        processPost(postId);
      }

      // lastPostId에 postIds.getLast() 대입
      lastPostId = postIds.getLast();
    }
  }

  private void processPost(Long postId) {
    // try 안에서 postDeadlineService.updateDeadlineStatus(postId) 호출
    try {
      postDeadlineService.updateDeadlineStatus(postId);
    } catch (BusinessException exception) {
      if (exception.getErrorCode() == PostErrorCode.POST_NOT_FOUND) {
        log.debug("마감 처리 대상 공고가 없어 건너뜁니다. postId={}", postId);
      } else {
        log.error("공고 마감 처리에 실패했습니다. postId={}", postId, exception);
      }
    } catch (RuntimeException exception) {
      log.error("공고 마감 처리에 실패했습니다. postId={}", postId, exception);
    }
  }
}
