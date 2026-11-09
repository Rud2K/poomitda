package com.poomitda.domain.post.validation;

import com.poomitda.domain.post.dto.PostRequestDto.PostListRequest;
import com.poomitda.global.constant.Region;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import com.poomitda.global.validation.DistrictCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostListValidator {

  private final DistrictCatalog districtCatalog;

  public void validate(PostListRequest request) {
    // request의 minAgeMonths와 maxAgeMonths를 validateAgeRange에 전달
    validateAgeRange(request.minAgeMonths(), request.maxAgeMonths());

    // request의 keyword를 validateKeyword에 전달
    validateKeyword(request.keyword());

    // request의 region과 district를 validateRegion에 전달
    validateRegion(request.region(), request.district());
  }

  private void validateAgeRange(Integer minAgeMonths, Integer maxAgeMonths) {
    // 둘 중 하나라도 null이면 비교할 필요가 없으므로 종료
    if (minAgeMonths == null || maxAgeMonths == null) return;

    // minAgeMonths가 maxAgeMonths보다 크면 INVALID_INPUT_VALUE 예외 발생
    if (minAgeMonths > maxAgeMonths) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }
  }

  private void validateKeyword(String keyword) {
    // keyword가 null이면 검색어를 생략한 것이므로 종료
    if (keyword == null) return;

    // DTO에서 앞뒤 공백이 제거된 검색어의 길이를 구함
    int keywordLength = keyword.length();

    // keywordLength가 0이거나 100보다 크면 INVALID_INPUT_VALUE 예외 발생
    if (keywordLength == 0 || keywordLength > 100) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }
  }

  private void validateRegion(Region region, String district) {
    // district가 null이면 시/군/구 검색을 생략한 것이므로 종료
    if (district == null) return;

    // district가 빈 문자열이면 INVALID_INPUT_VALUE 예외 발생
    if (district.isEmpty()) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    // region이 null이면 INVALID_INPUT_VALUE 예외 발생
    if (region == null) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    // 시/군/구가 지정한 광역 지역에 속하지 않으면 INVALID_INPUT_VALUE 예외 발생
    if (!districtCatalog.contains(region, district)) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }
  }
}
