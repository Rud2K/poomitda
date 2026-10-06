package com.poomitda.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
public final class ErrorResponse {

  private final String code;
  private final String message;
  private final Void data;

  private ErrorResponse(ErrorCode errorCode, String message) {
    this.code = errorCode.name();
    this.message = message;
    this.data = null;
  }

  // ErrorCode.java에 정의된 기본 메시지 사용
  public static ErrorResponse of(ErrorCode errorCode) {
    return new ErrorResponse(errorCode, errorCode.getMessage());
  }

  // 상황에 맞는 별도 메시지 사용
  public static ErrorResponse of(ErrorCode errorCode, String message) {
    return new ErrorResponse(errorCode, message);
  }
}
