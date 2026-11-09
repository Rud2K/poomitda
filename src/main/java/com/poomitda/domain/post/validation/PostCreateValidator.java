package com.poomitda.domain.post.validation;

import com.poomitda.domain.post.dto.PostRequestDto.*;
import com.poomitda.domain.post.entity.constant.NoticeType;
import com.poomitda.global.constant.Region;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import com.poomitda.global.validation.DistrictCatalog;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostCreateValidator {

  private final DistrictCatalog districtCatalog;

  public void validate(CreatePostRequest request) {
    // 공고 지역과 시/군/구 조합 검증
    validateRegion(request.region(), request.district());

    // 모집 유형에 따른 임시보호 희망 기간 검증
    validateFosterPeriod(request.noticeType(), request.fosterPeriod());

    // 대표 이미지 개수와 이미지 URL과 표시 순서 중복을 검증
    validateImage(request.images());
  }

  private void validateRegion(Region region, String district) {
    // region이 null이면 INVALID_INPUT_VALUE 예외 발생
    if (region == null) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    // district가 null이면 시/군/구를 생략한 것이므로 종료
    if (district == null) return;

    // district가 빈 문자열이면 INVALID_INPUT_VALUE 예외 발생
    if (district.isBlank()) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    // districtCatalog.contains(region, district)가 false면 INVALID_INPUT_VALUE 예외 발생
    if (!districtCatalog.contains(region, district)) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }
  }

  private void validateFosterPeriod(NoticeType noticeType, String fosterPeriod) {
    // 모집 유형이 FOSTER인 경우
    if (noticeType == NoticeType.FOSTER) {
      // fosterPeriod가 null이거나 공백 문자열인지 확인
      if (fosterPeriod == null || fosterPeriod.isBlank()) {
        throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
      }
    }

    // 모집 유형이 ADOPTION인 경우
    if (noticeType == NoticeType.ADOPTION) {
      // fosterPeriod가 null이 아닌지 확인
      if (fosterPeriod != null) {
        throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
      }
    }
  }

  private void validateImage(List<PostImageRequest> images) {
    // 이미지 URL 중복을 검사할 Set<String>을 HashSet으로 생성
    Set<String> imageUrls = new HashSet<>();

    // 표시 순서 중복을 검사할 Set<Integer>를 HashSet으로 생성
    Set<Integer> displayOrders = new HashSet<>();

    // 대표 이미지 개수를 셀 int 변수 초기화
    int representativeCount = 0;

    // 이미지 목록을 순회
    for (PostImageRequest image : images) {
      // 현재 이미지가 대표 이미지일 경우 개수를 1 증가
      if (image.isRepresentative()) {
        representativeCount++;
      }

      // URL을 Set에 추가
      if (!imageUrls.add(image.imageUrl())) {
        // 이미 존재하는 값일 경우 INVALID_INPUT_VALUE 예외 발생
        throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
      }

      // 표시 순서를 Set에 추가
      if (!displayOrders.add(image.displayOrder())) {
        // 이미 존재하는 값일 경우 INVALID_INPUT_VALUE 예외 발생
        throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
      }
    }

    // 순회가 끝난 뒤 대표 이미지 개수가 정확히 1개인지 확인
    if (representativeCount != 1) {
      // 1개가 아니라면 INVALID_INPUT_VALUE 예외 발생
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }
  }
}
