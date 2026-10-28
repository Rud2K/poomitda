package com.poomitda.domain.post.dto;

public final class PostResponseDto {

  private PostResponseDto() {}

  public record CreatePostResponse(
      Long postId
  ) {}

  public record ImageUploadResponse(
      String imageKey,
      String imageUrl
  ) {}
}
