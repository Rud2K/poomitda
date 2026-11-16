package com.poomitda.domain.post.repository;

import com.poomitda.domain.post.cursor.PostCursor;
import com.poomitda.domain.post.dto.PostRequestDto.PostListRequest;
import com.poomitda.domain.post.entity.Post;
import java.time.*;
import java.util.List;

public interface PostQueryRepository {

  /**
   * 검색 조건과 커서에 따라 삭제되지 않은 공고 목록을 조회한다.
   *
   * @param request 검증과 입력값 정리가 완료된 검색 요청
   * @param cursor 검증된 커서. 첫 페이지 조회에서는 null
   * @param referenceDate 나이 계산 기준일. 다음 페이지에서도 동일한 날짜를 사용
   * @param now 현재 요청에서 마감 여부를 판단할 UTC 기준 시각
   * @return 정렬 조건에 맞는 공고를 최대 size + 1개 담은 {@link List}
   */
  List<Post> findPosts(
      PostListRequest request,
      PostCursor cursor,
      LocalDate referenceDate,
      LocalDateTime now
  );

  /**
   * 검색 범위 안에서 마감 상태를 확인할 미종료 공고 ID를 조회한다.
   * 요청의 상태 필터와 페이지 커서는 적용하지 않는다.
   *
   * @param request 검증과 입력값 정리가 완료된 검색 요청
   * @param referenceDate 나이 계산 기준일
   * @param now 마감 대상 여부를 판단할 UTC 기준 시각
   * @param lastPostId 직전 묶음의 마지막 공고 ID. 최초 조회에서는 0
   * @param batchSize 한 번에 조회할 최대 개수. 양수여야 함
   * @return 대상 공고 ID를 오름차순으로 담은 {@link List}
   */
  List<Long> findDeadlineTargetIdsForSearch(
      PostListRequest request,
      LocalDate referenceDate,
      LocalDateTime now,
      Long lastPostId,
      int batchSize
  );
}
