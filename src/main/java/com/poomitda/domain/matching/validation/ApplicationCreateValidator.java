package com.poomitda.domain.matching.validation;

import com.poomitda.domain.matching.dto.ApplicationRequestDto.CreateApplicationRequest;
import com.poomitda.domain.matching.entity.constant.HousingOwnership;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class ApplicationCreateValidator {

  public void validate(CreateApplicationRequest request) {
    // 주거 소유 형태에 따른 임대인 동의 여부 검증
    validateLandlordAgreement(request.housingOwnership(), request.isLandlordAgreed());
  }

  private void validateLandlordAgreement(
      HousingOwnership housingOwnership,
      Boolean isLandlordAgreed
  ) {
    // 임대인 동의 여부를 반드시 입력해야 하는 주거 소유 형태인지 판단
    boolean requiresLandlordAgreement =
        housingOwnership == HousingOwnership.JEONSE
        || housingOwnership == HousingOwnership.MONTHLY_RENT;

    // 필수 입력 대상인데 isLandlordAgreed가 null이면 예외 발생
    if (requiresLandlordAgreement && isLandlordAgreed == null) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }
  }
}
