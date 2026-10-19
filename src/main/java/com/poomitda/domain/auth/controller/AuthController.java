package com.poomitda.domain.auth.controller;

import com.poomitda.domain.auth.dto.*;
import com.poomitda.domain.auth.service.AuthService;
import com.poomitda.global.response.SuccessResponse;
import com.poomitda.global.security.principal.MemberPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  @PostMapping("/login")
  public ResponseEntity<SuccessResponse<AuthResponseDto.LoginResponse>> login(
      @Valid @RequestBody AuthRequestDto.LoginRequest request
  ) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(
            SuccessResponse.of(
                "로그인에 성공했습니다.",
                authService.login(request)
            )
        );
  }

  @PostMapping("/logout")
  public ResponseEntity<SuccessResponse<Void>> logout(
      @AuthenticationPrincipal MemberPrincipal memberPrincipal
  ) {
    authService.logout(memberPrincipal.memberId());
    return ResponseEntity.ok()
        .body(
            SuccessResponse.of(
                "성공적으로 로그아웃되었습니다."
            )
        );
  }

  @PostMapping("/reissue")
  public ResponseEntity<SuccessResponse<AuthResponseDto.ReissueResponse>> reissue(
      @RequestHeader(value = "Refresh-Token", required = false) String refreshToken
  ) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(
            SuccessResponse.of(
                "토큰이 성공적으로 재발급되었습니다.",
                authService.reissue(refreshToken)
            )
        );
  }
}
