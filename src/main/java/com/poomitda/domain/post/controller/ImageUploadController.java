package com.poomitda.domain.post.controller;

import com.poomitda.domain.post.dto.PostResponseDto;
import com.poomitda.domain.post.service.ImageUploadService;
import com.poomitda.global.response.SuccessResponse;
import com.poomitda.global.security.principal.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/images")
public class ImageUploadController {

  private final ImageUploadService imageUploadService;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public SuccessResponse<PostResponseDto.ImageUploadResponse> upload(
      @AuthenticationPrincipal MemberPrincipal memberPrincipal,
      @RequestPart("file")MultipartFile file
  ) {
    return SuccessResponse.of(
        "이미지 업로드 처리 성공",
        imageUploadService.upload(memberPrincipal.memberId(), file)
    );
  }
}
