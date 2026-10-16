package com.poomitda.domain.auth.dto;

import jakarta.validation.constraints.*;

public final class AuthRequestDto {

  private AuthRequestDto() {}

  public record LoginRequest(
      @Email(message = "이메일 형식이 올바르지 않습니다.")
      @NotBlank(message = "이메일은 필수 입력 항목입니다.")
      @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
      String email,

      @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
      String password
  ) {}
}
