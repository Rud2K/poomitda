package com.poomitda.domain.post.cursor;

import com.poomitda.domain.post.dto.PostRequestDto.PostListRequest;
import com.poomitda.domain.post.dto.constant.PostSort;
import com.poomitda.domain.post.entity.Post;
import java.time.*;
import org.springframework.stereotype.Component;

@Component
public class PostCursorFactory {

  private static final int CURRENT_VERSION = 1;

  public PostCursor create(
      Post lastPost,
      PostListRequest request,
      LocalDate referenceDate
  ) {
    // LastPost, request, referenceDate가 null이면 각각 메시지를 포함한 IllegalArgumentException 발생
    if (lastPost == null) {
      throw new IllegalArgumentException("커서 생성 기준 공고는 null일 수 없습니다.");
    }
    if (request == null) {
      throw new IllegalArgumentException("공고 목록 조회 요청은 null일 수 없습니다.");
    }
    if (referenceDate == null) {
      throw new IllegalArgumentException("나이 계산 기준일은 null일 수 없습니다.");
    }

    // 마지막 공고 ID 검증
    if (lastPost.getPostId() == null || lastPost.getPostId() <= 0) {
      throw new IllegalArgumentException("커서 생성 기준 공고 ID는 양수여야 합니다.");
    }

    // request.sort()가 null이면 IllegalArgumentException 발생
    if (request.sort() == null) {
      throw new IllegalArgumentException("커서 생성에 사용할 정렬 조건은 필수입니다.");
    }

    // 정렬 기준값을 담을 변수 세 개를 null로 초기화
    LocalDateTime createdAt = null;
    Integer bookmarkCount = null;
    LocalDateTime recruitmentDeadline = null;

    // request.sort()를 switch로 분기
    switch (request.sort()) {
      case PostSort.LATEST -> {
        if (lastPost.getCreatedAt() == null) {
          throw new IllegalStateException("최신순 커서 생성에 필요한 공고 생성 시각이 없습니다.");
        }
        createdAt = lastPost.getCreatedAt();
      }
      case PostSort.BOOKMARK -> {
        if (lastPost.getBookmarkCount() < 0) {
          throw new IllegalStateException("공고의 북마크 수는 음수일 수 없습니다.");
        }
        bookmarkCount = lastPost.getBookmarkCount();
      }
      case PostSort.CLOSING -> {
        if (lastPost.getRecruitmentDeadline() == null) {
          throw new IllegalStateException("마감임박순 커서 생성에 필요한 접수 마감 시각이 없습니다.");
        }
        recruitmentDeadline = lastPost.getRecruitmentDeadline();
      }
    }

    // 현재 요청으로 검색 조건 객체 생성
    PostCursorFilter filters = toFilters(request);

    // new PostCursor() 반환
    return new PostCursor(
        CURRENT_VERSION,
        request.sort(),
        lastPost.getPostId(),
        createdAt,
        bookmarkCount,
        recruitmentDeadline,
        filters,
        referenceDate
    );
  }

  private PostCursorFilter toFilters(PostListRequest request) {
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
