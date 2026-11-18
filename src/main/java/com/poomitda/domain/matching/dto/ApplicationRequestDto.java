package com.poomitda.domain.matching.dto;

import com.poomitda.domain.matching.entity.constant.*;
import jakarta.validation.constraints.*;

public final class ApplicationRequestDto {

  private ApplicationRequestDto() {}

  public record CreateApplicationRequest(
      @NotNull(message = "공고 ID는 필수 입력 항목입니다.")
      @Positive(message = "공고 ID는 양수여야 합니다.")
      Long postId,

      @NotBlank(message = "직업은 필수 입력 항목입니다.")
      @Size(max = 50, message = "직업은 50자 이하여야 합니다.")
      String occupation,

      @NotNull(message = "주거 형태는 필수 입력 항목입니다.")
      HousingType housingType,

      @NotNull(message = "주거 소유 형태는 필수 입력 항목입니다.")
      HousingOwnership housingOwnership,

      Boolean isLandlordAgreed,

      @NotNull(message = "마당 또는 발코니 보유 여부는 필수 입력 항목입니다.")
      Boolean hasYardOrBalcony,

      @NotNull(message = "하루 평균 부재 시간은 필수 입력 항목입니다.")
      @Min(value = 0, message = "하루 평균 부재 시간은 0시간 이상이어야 합니다.")
      @Max(value = 24, message = "하루 평균 부재 시간은 24시간 이하여야 합니다.")
      Integer timeAwayPerDay,

      @NotNull(message = "가족 수는 필수 입력 항목입니다.")
      @Min(value = 0, message = "가족 수는 0명 이상이어야 합니다.")
      Integer familyCount,

      @NotBlank(message = "가족 연령 정보는 필수 입력 항목입니다.")
      @Size(max = 100, message = "가족 연령 정보는 100자 이하여야 합니다.")
      String familyAges,

      @NotNull(message = "동거인 동의 여부는 필수 입력 항목입니다.")
      Boolean hasCohabitantAgreed,

      @NotNull(message = "알레르기 유무는 필수 입력 항목입니다.")
      Boolean hasAllergy,

      @NotBlank(message = "현재 반려동물 정보는 필수 입력 항목입니다.")
      String currentPets,

      String pastExperienceDetail,

      @NotNull(message = "경제적 준비 여부는 필수 입력 항목입니다.")
      Boolean isFinanciallyReady,

      @NotNull(message = "서약 동의 여부는 필수 입력 항목입니다.")
      @AssertTrue(message = "입양 및 임시보호 서약에 동의해야 합니다.")
      Boolean pledgeAgreed
  ) {}

  public record MyApplicationListRequest(
      ApplicationStatus status,
      String cursor,

      @Min(value = 1, message = "조회 개수는 1 이상이어야 합니다.")
      @Max(value = 50, message = "조회 개수는 50 이하여야 합니다.")
      Integer size
  ) {
    public MyApplicationListRequest {
      if (size == null) {
        size = 10;
      }
    }
  }
}
