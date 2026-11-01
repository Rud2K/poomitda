package com.poomitda.domain.post.controller;

import com.poomitda.domain.post.dto.*;
import com.poomitda.domain.post.service.PostService;
import com.poomitda.global.response.SuccessResponse;
import com.poomitda.global.security.principal.MemberPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts")
public class PostController {

  private final PostService postService;

  @PostMapping
  public SuccessResponse<PostResponseDto.CreatePostResponse> createPost(
      @AuthenticationPrincipal MemberPrincipal memberPrincipal,
      @Valid @RequestBody PostRequestDto.CreatePostRequest request
  ) {
    return SuccessResponse.of(
        "공고가 성공적으로 등록되었습니다.",
        postService.createPost(memberPrincipal.memberId(), request)
    );
  }
}
