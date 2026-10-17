package com.poomitda.domain.auth.controller;

import com.poomitda.domain.auth.dto.*;
import com.poomitda.domain.auth.service.AuthService;
import com.poomitda.global.response.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
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
}
