package com.poomitda.domain.post.cursor;

import com.poomitda.domain.post.dto.PostRequestDto;
import com.poomitda.domain.post.dto.constant.PostSort;
import com.poomitda.domain.post.exception.PostErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class PostCursorValidator {

  private static final int SUPPORTED_VERSION = 1;

  public void validate(PostCursor cursor, PostRequestDto.PostListRequest request) {
    // request가 null이면 IllegalArgumentException 발생
    if (request == null) {
      throw new IllegalArgumentException("공고 목록 조회 요청은 null일 수 없습니다.");
    }

    // validateCommon(cursor) 호출
    validateCommon(cursor);

    // cursor.sort()와 request.sort()가 다르면 INVALID_CURSOR 발생
    if (cursor.sort() != request.sort()) {
      throw new BusinessException(PostErrorCode.INVALID_CURSOR);
    }

    // 현재 요청으로 검색 조건 객체 생성
    PostCursorFilter currentFilters = toFilters(request);

    // cursor.filters()와 currentFilters가 다르면 INVALID_CURSOR 발생
    if (!cursor.filters().equals(currentFilters)) {
      throw new BusinessException(PostErrorCode.INVALID_CURSOR);
    }

    // validateSortValue(cursor) 호출
    validateSortValue(cursor);
  }

  private void validateCommon(PostCursor cursor) {
    // cursor가 null이면 INVALID_CURSOR 발생
    if (cursor == null) {
      throw new BusinessException(PostErrorCode.INVALID_CURSOR);
    }

    // version이 null이거나 SUPPORTED_VERSION과 다르면 INVALID_CURSOR 발생
    if (cursor.version() == null || cursor.version() != SUPPORTED_VERSION) {
      throw new BusinessException(PostErrorCode.INVALID_CURSOR);
    }

    // postId가 null이거나 0 이하이면 INVALID_CURSOR 발생
    if (cursor.postId() == null || cursor.postId() <= 0) {
      throw new BusinessException(PostErrorCode.INVALID_CURSOR);
    }

    // sort, filters, referenceDate 중 하나라도 null이면 INVALID_CURSOR 발생
    if (cursor.sort() == null || cursor.filters() == null || cursor.referenceDate() == null) {
      throw new BusinessException(PostErrorCode.INVALID_CURSOR);
    }
  }

  private void validateSortValue(PostCursor cursor) {
    // cursor.sort()를 switch로 분기
    switch (cursor.sort()) {
      case PostSort.LATEST -> {
        if (cursor.createdAt() == null
            || cursor.bookmarkCount() != null
            || cursor.recruitmentDeadline() != null) {
          throw new BusinessException(PostErrorCode.INVALID_CURSOR);
        }
      }

      case PostSort.BOOKMARK -> {
        if (cursor.bookmarkCount() == null
            || cursor.bookmarkCount() < 0
            || cursor.createdAt() != null
            || cursor.recruitmentDeadline() != null) {
          throw new BusinessException(PostErrorCode.INVALID_CURSOR);
        }
      }

      case PostSort.CLOSING -> {
        if (cursor.recruitmentDeadline() == null
            || cursor.createdAt() != null
            || cursor.bookmarkCount() != null) {
          throw new BusinessException(PostErrorCode.INVALID_CURSOR);
        }
      }
    }
  }

  private PostCursorFilter toFilters(PostRequestDto.PostListRequest request) {
    return new PostCursorFilter(
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
  }
}
