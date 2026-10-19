package com.poomitda.domain.member.dto;

import com.poomitda.domain.member.entity.constant.*;
import com.poomitda.global.constant.*;

public final class MemberResponseDto {

  private MemberResponseDto() {}

  public record SignupResponse(
      Long memberId,
      String email,
      String nickname
  ) {}

  public record MyInfoResponse(
      Long memberId,
      String email,
      String nickname,
      String profileImageUrl,
      Region preferredRegion,
      Species preferredSpecies,
      MemberRole role,
      MemberStatus status,
      Double averageRating,
      int reviewCount
  ) {}
}
