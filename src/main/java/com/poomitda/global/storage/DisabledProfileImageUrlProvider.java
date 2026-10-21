package com.poomitda.global.storage;

import org.jspecify.annotations.Nullable;

public class DisabledProfileImageUrlProvider implements ProfileImageUrlProvider {

  /**
   * 이미지 기능이 비활성화된 상태일 때 null을 반환한다.
   *
   * @param profileImageKey 회원의 이미지 저장 키
   * @return null
   */
  @Override
  public @Nullable String getUrl(@Nullable String profileImageKey) {
    return null;
  }
}
