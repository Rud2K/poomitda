package com.poomitda.domain.matching.cursor;

import com.poomitda.domain.matching.dto.ApplicationRequestDto.PostApplicationListRequest;
import com.poomitda.domain.matching.entity.Application;
import org.springframework.stereotype.Component;

@Component
public class PostApplicationCursorFactory {

  private static final int CURRENT_VERSION = 1;

  /**
   * 실제 반환 목록의 마지막 신청과 현재 조회 조건으로 커서를 생성한다.
   *
   * @param lastApplication 실제 반환 목록의 마지막 신청
   * @param memberId 현재 로그인한 회원 ID
   * @param request 현재 공고별 신청 목록 조회 요청
   * @return 다음 페이지 조회에 사용할 커서
   * @throws IllegalArgumentException 신청 또는 요청이 null이거나 ID가 유효하지 않은 경우
   * @throws IllegalStateException 신청의 생성 시각이 없는 경우
   */
  public PostApplicationCursor create(
      Application lastApplication,
      Long memberId,
      PostApplicationListRequest request
  ) {
    // 커서 생성에 필요한 내부 인자 확인
    if (lastApplication == null) {
      throw new IllegalArgumentException("커서 생성 기준 신청은 null일 수 없습니다.");
    }

    if (memberId == null || memberId <= 0) {
      throw new IllegalArgumentException("현재 회원 ID는 양수여야 합니다.");
    }

    if (request == null) {
      throw new IllegalArgumentException("공고별 신청 목록 조회 요청은 null일 수 없습니다.");
    }

    if (request.postId() == null || request.postId() <= 0) {
      throw new IllegalArgumentException("조회할 공고 ID는 양수여야 합니다.");
    }

    // 마지막 신청의 정렬 기준값 확인
    if (lastApplication.getApplicationId() == null || lastApplication.getApplicationId() <= 0) {
      throw new IllegalArgumentException("커서 생성 기준 신청 ID는 양수여야 합니다.");
    }

    if (lastApplication.getCreatedAt() == null) {
      throw new IllegalStateException("커서 생성에 필요한 신청 생성 시각이 없습니다.");
    }

    // 현재 회원, 공고, 상태 필터 및 마지막 신청 위치를 커서에 저장
    return new PostApplicationCursor(
        CURRENT_VERSION,
        memberId,
        request.postId(),
        request.status(),
        lastApplication.getCreatedAt(),
        lastApplication.getApplicationId()
    );
  }
}
