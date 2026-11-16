package com.poomitda.domain.post.service;

import com.poomitda.domain.post.dto.PostRequestDto.PostListRequest;
import com.poomitda.domain.post.exception.PostErrorCode;
import com.poomitda.domain.post.repository.PostRepository;
import com.poomitda.global.exception.exception.BusinessException;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class PostListDeadlineService {

  private static final int BATCH_SIZE = 100;

  private final PostRepository postRepository;
  private final PostDeadlineService postDeadlineService;

  /**
   * 검색 범위 안에서 기준 시각에 마감된 공고의 상태를 갱신한다.
   * 사라진 공고는 건너뛰고, 그 외 처리 실패는 호출자에게 전달한다.
   *
   * @param request 검증된 목록 검색 요청
   * @param referenceDate 나이 계산 기준일
   * @param now 마감 대상 조회에 사용할 고정된 UTC 기준 시각
   */
  @Transactional(propagation = Propagation.NEVER)
  public void updateExpiredPostsForSearch(
      PostListRequest request,
      LocalDate referenceDate,
      LocalDateTime now
  ) {
    // 전달받은 인자 검증
    if (request == null || referenceDate == null || now == null) {
      throw new IllegalArgumentException(
          "목록 마감 처리에는 검색 요청, 나이 기준일, 마감 기준 시각이 필요합니다."
      );
    }

    // 최초 조회 위치 초기화
    Long lastPostId = 0L;

    while (true) {
      // 다음 묶음의 마감 처리 대상 조회
      List<Long> postIds = postRepository.findDeadlineTargetIdsForSearch(
          request,
          referenceDate,
          now,
          lastPostId,
          BATCH_SIZE
      );

      // 더 이상 처리할 대상이 없으면 메서드 종료
      if (postIds.isEmpty()) {
        return;
      }

      // 조회한 공고를 한 건씩 처리
      for (Long postId : postIds) {
        try {
          // 공고의 마감 상태 갱신
          postDeadlineService.updateDeadlineStatus(postId);
        } catch (BusinessException exception) {
          // 사라진 공고만 건너뛰고, 다른 오류는 호출자에게 전달
          if (exception.getErrorCode() != PostErrorCode.POST_NOT_FOUND) {
            throw exception;
          }
        }
      }

      // 다음 묶음을 조회할 위치 갱신
      lastPostId = postIds.getLast();
    }
  }
}
