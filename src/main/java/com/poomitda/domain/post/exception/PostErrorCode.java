package com.poomitda.domain.post.exception;

import com.poomitda.global.exception.ErrorCode;
import lombok.*;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PostErrorCode implements ErrorCode {

  // 공고가 존재하지 않거나 소프트 삭제된 경우
  POST_NOT_FOUND(
      HttpStatus.NOT_FOUND,
      "존재하지 않는 공고입니다."
  );

  private final HttpStatus httpStatus;
  private final String message;

  @Override
  public String getCode() {
    return name();
  }
}
