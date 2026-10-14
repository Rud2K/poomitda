package com.poomitda.domain.member.controller;

import com.poomitda.domain.member.dto.*;
import com.poomitda.domain.member.service.MemberService;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import com.poomitda.global.response.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
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

  @GetMapping("/check-email")
  public SuccessResponse<Void> checkEmail(
      @Valid @ModelAttribute MemberRequestDto.CheckEmailRequest request,
      BindingResult bindingResult
  ) {
    if (bindingResult.hasErrors()) {
      throw new BusinessException(
          CommonErrorCode.INVALID_INPUT_VALUE,
          "이메일 형식이 올바르지 않습니다."
      );
    }

    memberService.checkEmail(request.email());
    return SuccessResponse.of("사용 가능한 이메일입니다.");
  }

  @GetMapping("/check-nickname")
  public SuccessResponse<Void> checkNickname(
      @Valid @ModelAttribute MemberRequestDto.CheckNicknameRequest request,
      BindingResult bindingResult
  ) {
    if (bindingResult.hasErrors()) {
      throw new BusinessException(
          CommonErrorCode.INVALID_INPUT_VALUE,
          "닉네임 형식이 올바르지 않습니다."
      );
    }

    memberService.checkNickname(request.nickname());
    return SuccessResponse.of("사용 가능한 닉네임입니다.");
  }
}
