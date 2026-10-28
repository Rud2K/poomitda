package com.poomitda.domain.post.dto;

import com.poomitda.domain.post.entity.constant.*;
import com.poomitda.global.constant.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public final class PostRequestDto {

  private PostRequestDto() {}

  public record CreatePostRequest(
      @NotBlank(message = "제목은 필수 입력 항목입니다.")
      @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
      String title,

      @NotBlank(message = "본문은 필수 입력 항목입니다.")
      String content,

      @NotNull(message = "모집 유형은 필수 입력 항목입니다.")
      NoticeType noticeType,

      @NotNull(message = "동물 종류는 필수 입력 항목입니다.")
      Species species,

      @NotBlank(message = "품종은 필수 입력 항목입니다.")
      @Size(max = 50, message = "품종은 50자 이하여야 합니다.")
      String breed,

      @PastOrPresent(message = "추정 출생일은 현재 또는 과거 날짜여야 합니다.")
      LocalDate estimatedBirthDate,

      @NotNull(message = "성별은 필수 입력 항목입니다.")
      Gender gender,

      @NotNull(message = "중성화 여부는 필수 입력 항목입니다.")
      Boolean isNeutered,

      @Size(max = 100, message = "임시보호 희망 기간은 100자 이하여야 합니다.")
      String fosterPeriod,

      @Size(max = 255, message = "발견 장소는 255자 이하여야 합니다.")
      String foundLocation,

      @NotNull(message = "지역은 필수 입력 항목입니다.")
      Region region,

      @Size(max = 50, message = "시/군/구는 50자 이하여야 합니다.")
      String district,

      @Future(message = "접수 마감 시각은 현재보다 미래여야 합니다.")
      OffsetDateTime recruitmentDeadline,

      @NotEmpty(message = "공고 이미지는 최소 1개 이상 필요합니다.")
      List<
          @NotNull(message = "공고 이미지 항목은 null일 수 없습니다.")
          @Valid
          PostImageRequest
      > images
  ) {}

  public record PostImageRequest(
      @NotBlank(message = "이미지 URL은 필수 입력 항목입니다.")
      @Size(max = 255, message = "이미지 URL은 255자 이하여야 합니다.")
      String imageUrl,

      @NotNull(message = "대표 이미지 여부는 필수 입력 항목입니다.")
      Boolean isRepresentative,

      @NotNull(message = "이미지 표시 순서는 필수 입력 항목입니다.")
      @PositiveOrZero(message = "이미지 표시 순서는 0 이상이어야 합니다.")
      Integer displayOrder
  ) {}
}
