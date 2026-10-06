package com.poomitda.global.exception.exception;

import com.poomitda.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;

  // ErrorCode에 정의된 기본 메시지 사용
  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  // 상황에 맞는 별도 메시지 사용
  public BusinessException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }
}
