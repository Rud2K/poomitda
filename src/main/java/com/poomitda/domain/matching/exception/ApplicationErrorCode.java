package com.poomitda.domain.matching.exception;

import com.poomitda.global.exception.ErrorCode;
import lombok.*;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ApplicationErrorCode implements ErrorCode {

  // 신청이 APPLIED 상태가 아니어서 심사를 시작할 수 없는 경우
  APPLICATION_CANNOT_BE_REVIEWED(
      HttpStatus.CONFLICT,
      "현재 상태에서는 신청 심사를 시작할 수 없습니다."
  ),

  // 공고가 접수 중 또는 접수 마감 상태가 아닌 경우
  POST_ALREADY_CLOSED(
      HttpStatus.CONFLICT,
      "모집 절차가 종료된 공고의 신청은 심사할 수 없습니다."
  ),

  // 신청이 APPLIED 상태가 아니어서 취소할 수 없는 경우
  APPLICATION_CANNOT_BE_CANCELLED(
      HttpStatus.CONFLICT,
      "현재 상태에서는 신청을 취소할 수 없습니다."
  ),

  // 커서 형식이 잘못되었거나 현재 조회 조건과 일치하지 않는 경우
  INVALID_CURSOR(
      HttpStatus.BAD_REQUEST,
      "유효하지 않은 커서입니다."
  ),

  // 요청한 신청이 존재하지 않는 경우
  APPLICATION_NOT_FOUND(
      HttpStatus.NOT_FOUND,
      "존재하지 않는 신청입니다."
  ),

  // 취소와 거절을 포함하여 동일 공고에 신청한 이력이 있는 경우
  APPLICATION_ALREADY_EXISTS(
      HttpStatus.CONFLICT,
      "이미 신청한 공고입니다."
  ),

  // 접수 마감 시각과 현재 시각이 같거나 마감 시각이 지난 경우
  APPLICATION_DEADLINE_PASSED(
      HttpStatus.CONFLICT,
      "신청 접수 기한이 지났습니다."
  ),

  // 공고가 모집 중 상태가 아닌 경우
  POST_NOT_RECRUITING(
      HttpStatus.CONFLICT,
      "현재 모집 중인 공고가 아닙니다."
  ),

  // 신청자가 공고 작성자인 경우
  CANNOT_APPLY_OWN_POST(
      HttpStatus.CONFLICT,
      "본인이 작성한 공고에는 신청할 수 없습니다."
  );

  private final HttpStatus httpStatus;
  private final String message;

  @Override
  public String getCode() {
    return name();
  }
}
