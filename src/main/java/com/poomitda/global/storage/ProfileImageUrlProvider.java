package com.poomitda.global.storage;

import org.jspecify.annotations.Nullable;

public interface ProfileImageUrlProvider {

  /**
   * 이미지 저장 키에 대한 조회 URL을 반환한다.
   *
   * @param profileImageKey 회원의 이미지 저장 키, 이미지가 없으면 null
   * @return 이미지 조회 URL, 이미지가 없거나 기능이 비활성화 된 경우 null
   */
  @Nullable
  String getUrl(@Nullable String profileImageKey);
}
