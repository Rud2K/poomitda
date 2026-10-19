package com.poomitda.domain.member.exception;

import com.poomitda.global.exception.ErrorCode;
import lombok.*;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

  // 탈퇴 회원을 포함하여 동일한 이메일의 회원이 존재하는 경우
  DUPLICATE_EMAIL(
      HttpStatus.CONFLICT,
      "이미 존재하거나 사용 중인 이메일입니다."
  ),

  // 탈퇴 회원을 포함하여 동일한 닉네임의 회원이 존재하는 경우
  DUPLICATE_NICKNAME(
      HttpStatus.CONFLICT,
      "이미 존재하거나 사용 중인 닉네임입니다."
  ),

  // 주석 추가 예정
  MEMBER_NOT_FOUND(
      HttpStatus.NOT_FOUND,
      "존재하지 않는 회원입니다."
  );

  private final HttpStatus httpStatus;
  private final String message;

  @Override
  public String getCode() {
    return name();
  }
}
