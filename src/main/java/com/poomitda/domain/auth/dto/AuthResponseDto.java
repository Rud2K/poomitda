package com.poomitda.domain.auth.dto;

import com.poomitda.domain.member.entity.constant.MemberRole;

public final class AuthResponseDto {

  private AuthResponseDto() {}

  public record LoginResponse(
      String accessToken,
      String refreshToken,
      Long memberId,
      String nickname,
      MemberRole role
  ) {}
}
