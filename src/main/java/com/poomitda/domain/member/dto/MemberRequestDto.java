package com.poomitda.domain.member.dto;

import com.poomitda.global.constant.*;
import com.poomitda.global.request.PatchField;
import jakarta.validation.constraints.*;

public final class MemberRequestDto {

  private MemberRequestDto() {}

  public record SignupRequest(
      @Email(message = "이메일 형식이 올바르지 않습니다.")
      @NotBlank(message = "이메일은 필수 입력 항목입니다.")
      @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
      String email,

      @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
      String password,

      @NotBlank(message = "닉네임은 필수 입력 항목입니다.")
      @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
      String nickname,

      Region preferredRegion,
      Species preferredSpecies
  ) {}

  public record CheckEmailRequest(
      @Email(message = "이메일 형식이 올바르지 않습니다.")
      @NotBlank(message = "이메일은 필수 입력 항목입니다.")
      @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
      String email
  ) {}

  public record CheckNicknameRequest(
      @NotBlank(message = "닉네임은 필수 입력 항목입니다.")
      @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
      String nickname
  ) {}

  public record UpdateMyInfoRequest(
      PatchField<
          @NotBlank(message = "닉네임은 null이거나 공백일 수 없습니다.")
          @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
          String
      > nickname,

      PatchField<Region> preferredRegion,
      PatchField<Species> preferredSpecies
  ) {}
}
