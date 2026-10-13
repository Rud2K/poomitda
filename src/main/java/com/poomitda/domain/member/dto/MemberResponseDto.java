package com.poomitda.domain.member.dto;

public final class MemberResponseDto {

  private MemberResponseDto() {}

  public record SignupResponse(
      Long memberId,
      String email,
      String nickname
  ) {}
}
