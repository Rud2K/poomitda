package com.poomitda.domain.member.controller;

import com.poomitda.domain.member.dto.*;
import com.poomitda.domain.member.service.MemberService;
import com.poomitda.global.response.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberController {

  private final MemberService memberService;

  @PostMapping("/signup")
  public SuccessResponse<MemberResponseDto.SignupResponse> signup(
      @Valid @RequestBody MemberRequestDto.SignupRequest request
  ) {
    return SuccessResponse.of(
        "회원 가입이 완료되었습니다.",
        memberService.signup(request)
    );
  }
}
