package com.poomitda.domain.post.service;

import com.poomitda.domain.post.cursor.*;
import com.poomitda.domain.post.dto.PostRequestDto.PostListRequest;
import com.poomitda.domain.post.dto.PostResponseDto;
import com.poomitda.domain.post.dto.PostResponseDto.PostListResponse;
import com.poomitda.domain.post.validation.PostListValidator;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class PostListService {

  private final PostListValidator postListValidator;
  private final PostCursorCodec postCursorCodec;
  private final PostCursorValidator postCursorValidator;
  private final PostListDeadlineService postListDeadlineService;
  private final PostService postService;

  /**
   * 검색 요청과 커서를 검증하고 마감 상태 갱신 후 공고 목록을 조회한다.
   *
   * @param request 기본 입력값 검증이 완료된 목록 검색 요청
   * @return 공고 목록과 다음 페이지 정보를 담은 {@link PostListResponse}
   */
  @Transactional(propagation = Propagation.NEVER)
  public PostResponseDto.PostListResponse getPostList(PostListRequest request) {
    // 검색 요청의 존재 여부 확인
    if (request == null) {
      throw new IllegalArgumentException(
          "공고 목록 조회 요청은 null일 수 없습니다."
      );
    }

    // 검색 조건의 조합 검증
    postListValidator.validate(request);

    // 첫 페이지에 사용할 커서 초기화
    PostCursor cursor = null;

    // 커서가 전달된 경우 복원하고 검증
    if (request.cursor() != null) {
      cursor = postCursorCodec.decode(request.cursor());
      postCursorValidator.validate(cursor, request);
    }

    // 현재 요청의 마감 판단 시각 결정
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

    // 나이 계산 기준일 결정
    LocalDate referenceDate;

    if (cursor == null) {
      // 첫 페이지
      referenceDate = now.toLocalDate();
    } else {
      // 다음 페이지
      referenceDate = cursor.referenceDate();
    }

    // 검색 범위의 마감 상태 갱신
    postListDeadlineService.updateExpiredPostsForSearch(
        request,
        referenceDate,
        now
    );

    // 갱신 완료 후 목록을 조회해 반환
    return postService.getPostList(
        request,
        cursor,
        referenceDate,
        now
    );
  }
}
