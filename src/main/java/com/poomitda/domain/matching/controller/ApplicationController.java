package com.poomitda.domain.matching.controller;

import com.poomitda.domain.matching.dto.ApplicationRequestDto.CreateApplicationRequest;
import com.poomitda.domain.matching.dto.ApplicationResponseDto;
import com.poomitda.domain.matching.service.ApplicationService;
import com.poomitda.global.response.SuccessResponse;
import com.poomitda.global.security.principal.MemberPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/applications")
public class ApplicationController {

  private final ApplicationService applicationService;

  @PostMapping
  public SuccessResponse<ApplicationResponseDto.CreateApplicationResponse> createApplication(
      @AuthenticationPrincipal MemberPrincipal memberPrincipal,
      @Valid @RequestBody CreateApplicationRequest request
  ) {
    return SuccessResponse.of(
        "신청서가 제출되었습니다.",
        applicationService.createApplication(memberPrincipal.memberId(), request)
    );
  }

  @GetMapping("/{applicationId}")
  public SuccessResponse<ApplicationResponseDto.ApplicationDetailResponse> getApplicationDetail(
      @AuthenticationPrincipal MemberPrincipal memberPrincipal,
      @PathVariable("applicationId") Long applicationId
  ) {
    return SuccessResponse.of(
        "신청 상세 조회 성공",
        applicationService.getApplicationDetail(
            memberPrincipal.memberId(),
            applicationId
        )
    );
  }
}
