package com.poomitda.domain.member.exception;

import com.poomitda.global.exception.exception.BusinessException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

public final class MemberExceptionTranslator {

  private MemberExceptionTranslator() {}

  /**
   * 회원가입 중 발생한 데이터 무결성 예외를 제약 이름에 따라 반환
   *
   * @param exception 저장 중 발생한 데이터 무결성 예외
   * @return 이메일 또는 닉네임 중복 예외, 분류할 수 없으면 기존 예외
   */
  public static RuntimeException translateSignupException(
      DataIntegrityViolationException exception
  ) {
    Throwable cause = exception;

    while (cause != null) {
      if (cause instanceof ConstraintViolationException violation) {
        String constraintName = violation.getConstraintName();

        if (constraintName != null) {
          int separatorIndex = constraintName.lastIndexOf('.');
          String simpleName = constraintName.substring(separatorIndex + 1);

          if ("uk_members_email".equalsIgnoreCase(simpleName)) {
            return new BusinessException(MemberErrorCode.DUPLICATE_EMAIL);
          }

          if ("uk_members_nickname".equalsIgnoreCase(simpleName)) {
            return new BusinessException(MemberErrorCode.DUPLICATE_NICKNAME);
          }
        }
      }

      cause = cause.getCause();
    }

    return exception;
  }
}
