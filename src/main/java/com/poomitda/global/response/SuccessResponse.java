package com.poomitda.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
public final class SuccessResponse<T> {

  private final String code;
  private final String message;
  private final T data;

  private SuccessResponse(String message, T data) {
    this.code = "SUCCESS";
    this.message = message;
    this.data = data;
  }

  // 응답 데이터가 있는 성공 응답
  public static <T> SuccessResponse<T> of(String message, T data) {
    return new SuccessResponse<>(message, data);
  }

  // 응답 데이터가 없는 성공 응답
  public static SuccessResponse<Void> of(String message) {
    return new SuccessResponse<>(message, null);
  }
}
