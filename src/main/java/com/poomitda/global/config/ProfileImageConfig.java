package com.poomitda.global.config;

import com.poomitda.global.storage.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration
@EnableConfigurationProperties(ProfileImageProperties.class)
public class ProfileImageConfig {

  /**
   * 이미지 기능이 비활성화 된 경우 사용할 객체를 등록한다.
   *
   * @return 비활성화된 프로필 이미지 URL 제공 객체
   */
  @Bean
  @ConditionalOnProperty(
      prefix = "app.profile-image",
      name = "mode",
      havingValue = "disabled"
  )
  public ProfileImageUrlProvider profileImageUrlProvider() {
    return new DisabledProfileImageUrlProvider();
  }
}
