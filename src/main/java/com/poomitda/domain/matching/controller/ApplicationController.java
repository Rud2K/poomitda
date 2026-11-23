package com.poomitda.domain.matching.controller;

import com.poomitda.domain.matching.dto.*;
import com.poomitda.domain.matching.service.*;
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
  private final MyApplicationListService myApplicationListService;
  private final PostApplicationListService postApplicationListService;

  @PostMapping
  public SuccessResponse<ApplicationResponseDto.CreateApplicationResponse> createApplication(
      @AuthenticationPrincipal MemberPrincipal memberPrincipal,
      @Valid @RequestBody ApplicationRequestDto.CreateApplicationRequest request
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

  @GetMapping("/me")
  public SuccessResponse<ApplicationResponseDto.MyApplicationListResponse> getMyApplications(
      @AuthenticationPrincipal MemberPrincipal memberPrincipal,
      @Valid @ModelAttribute ApplicationRequestDto.MyApplicationListRequest request
  ) {
    return SuccessResponse.of(
        "내가 작성한 신청 목록 조회 성공",
        myApplicationListService.getMyApplications(
            memberPrincipal.memberId(),
            request
        )
    );
  }

  @GetMapping
  public SuccessResponse<ApplicationResponseDto.PostApplicationListResponse> getPostApplications(
      @AuthenticationPrincipal MemberPrincipal memberPrincipal,
      @Valid @ModelAttribute ApplicationRequestDto.PostApplicationListRequest request
  ) {
    return SuccessResponse.of(
        "공고에 접수된 신청 목록 조회 성공",
        postApplicationListService.getPostApplications(
            memberPrincipal.memberId(),
            request
        )
    );
  }

  @PatchMapping("/{applicationId}/cancel")
  public SuccessResponse<ApplicationResponseDto.ApplicationCancelResponse> cancelApplication(
      @AuthenticationPrincipal MemberPrincipal memberPrincipal,
      @PathVariable("applicationId") Long applicationId
  ) {
    return SuccessResponse.of(
        "신청이 취소되었습니다.",
        applicationService.cancelApplication(
            memberPrincipal.memberId(),
            applicationId
        )
    );
  }

  @PatchMapping("/{applicationId}/review")
  public SuccessResponse<ApplicationResponseDto.ApplicationReviewResponse> reviewApplication(
      @AuthenticationPrincipal MemberPrincipal memberPrincipal,
      @PathVariable("applicationId") Long applicationId
  ) {
    return SuccessResponse.of(
        "신청 심사가 시작되었습니다.",
        applicationService.reviewApplication(
            memberPrincipal.memberId(),
            applicationId
        )
    );
  }
}
