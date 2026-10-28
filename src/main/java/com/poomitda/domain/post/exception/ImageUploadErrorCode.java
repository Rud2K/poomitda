package com.poomitda.domain.post.exception;

import com.poomitda.global.exception.ErrorCode;
import lombok.*;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ImageUploadErrorCode implements ErrorCode {

  // 지원하지 않는 파일 형식이거나 유효한 이미지가 아닌 경우
  UPLOAD_NOT_ALLOWED(
      HttpStatus.BAD_REQUEST,
      "JPEG, PNG, WebP 형식의 유효한 이미지만 업로드할 수 있습니다."
  );

  private final HttpStatus httpStatus;
  private final String message;

  @Override
  public String getCode() {
    return name();
  }
}
