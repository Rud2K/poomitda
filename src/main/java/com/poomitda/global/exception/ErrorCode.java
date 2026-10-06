package com.poomitda.global.exception;

import lombok.*;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

  // 요청 데이터의 형식 또는 값이 유효하지 않은 경우
  INVALID_INPUT_VALUE(
      HttpStatus.BAD_REQUEST,
      "입력값이 올바르지 않습니다."
  ),

  // 인증 정보가 없거나 유효하지 않은 경우
  UNAUTHORIZED(
      HttpStatus.UNAUTHORIZED,
      "인증 정보가 유효하지 않습니다."
  ),

  // 해당 요청을 수행할 권한이 없는 경우
  FORBIDDEN(
      HttpStatus.FORBIDDEN,
      "해당 요청을 수행할 권한이 없습니다."
  ),

  // 예상하지 못한 서버 내부 오류가 발생한 경우
  INTERNAL_SERVER_ERROR(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "서버 내부 오류가 발생했습니다."
  );

  private final HttpStatus httpStatus;
  private final String message;
}
