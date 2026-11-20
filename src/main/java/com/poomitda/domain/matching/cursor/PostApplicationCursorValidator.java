package com.poomitda.domain.matching.cursor;

import com.poomitda.domain.matching.dto.ApplicationRequestDto.PostApplicationListRequest;
import com.poomitda.domain.matching.exception.ApplicationErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class PostApplicationCursorValidator {

  private static final int SUPPORTED_VERSION = 1;

  /**
   * 커서의 필수값과 현재 회원, 공고 및 상태 필터의 일치 여부를 검증한다.
   *
   * @param cursor 복원된 공고별 신청 목록 커서
   * @param memberId 인증과 권한 검증을 통과한 현재 회원 ID
   * @param request 기본 입력 검증을 통과한 현재 목록 조회 요청
   * @throws IllegalArgumentException 현재 회원 ID 또는 요청의 공고 ID가 유효하지 않거나 요청이 null인 경우
   * @throws BusinessException 커서가 유효하지 않거나 현재 조회 조건과 다른 경우
   */
  public void validate(
      PostApplicationCursor cursor,
      Long memberId,
      PostApplicationListRequest request
  ) {
    // 내부 호출에 필요한 인자 확인
    if (memberId == null || memberId <= 0) {
      throw new IllegalArgumentException("현재 회원 ID는 양수여야 합니다.");
    }

    if (request == null) {
      throw new IllegalArgumentException("공고별 신청 목록 조회 요청은 null일 수 없습니다.");
    }

    if (request.postId() == null || request.postId() <= 0) {
      throw new IllegalArgumentException("조회할 공고 ID는 양수여야 합니다.");
    }

    // 커서의 필수값과 지원 버전 확인
    validateCommon(cursor);

    // 현재 로그인 회원과 커서의 회원이 일치하는지 확인
    if (!memberId.equals(cursor.memberId())) {
      throw new BusinessException(ApplicationErrorCode.INVALID_CURSOR);
    }

    // 현재 조회 공고와 커서 생성 당시의 공고가 일치하는지 확인
    if (!request.postId().equals(cursor.postId())) {
      throw new BusinessException(ApplicationErrorCode.INVALID_CURSOR);
    }

    // 현재 상태 필터와 커서 생성 당시의 필터가 일치하는지 확인
    if (cursor.status() != request.status()) {
      throw new BusinessException(ApplicationErrorCode.INVALID_CURSOR);
    }
  }

  private void validateCommon(PostApplicationCursor cursor) {
    if (cursor == null) {
      throw new BusinessException(ApplicationErrorCode.INVALID_CURSOR);
    }

    if (cursor.version() == null || cursor.version() != SUPPORTED_VERSION) {
      throw new BusinessException(ApplicationErrorCode.INVALID_CURSOR);
    }

    if (cursor.memberId() == null || cursor.memberId() <= 0) {
      throw new BusinessException(ApplicationErrorCode.INVALID_CURSOR);
    }

    if (cursor.postId() == null || cursor.postId() <= 0) {
      throw new BusinessException(ApplicationErrorCode.INVALID_CURSOR);
    }

    if (cursor.applicationId() == null || cursor.applicationId() <= 0) {
      throw new BusinessException(ApplicationErrorCode.INVALID_CURSOR);
    }

    if (cursor.createdAt() == null) {
      throw new BusinessException(ApplicationErrorCode.INVALID_CURSOR);
    }
  }
}
