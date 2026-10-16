package com.poomitda.domain.auth.exception;

import com.poomitda.global.exception.ErrorCode;
import lombok.*;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

  LOGIN_FAILED(
      HttpStatus.UNAUTHORIZED,
      "이메일 또는 비밀번호가 일치하지 않습니다."
  );

  private final HttpStatus httpStatus;
  private final String message;

  @Override
  public String getCode() {
    return name();
  }
}
