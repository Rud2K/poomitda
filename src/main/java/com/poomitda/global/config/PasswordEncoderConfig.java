package com.poomitda.global.config;

import org.springframework.context.annotation.*;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

  /**
   * 비밀번호 인코딩과 일치 여부 검증에 사용할 인코더 등록
   *
   * @return 알고리즘 식별자를 지원하는 비밀번호 인코더
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }
}
