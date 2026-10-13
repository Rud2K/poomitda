package com.poomitda.global.config;

import com.poomitda.global.security.handler.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

  private final CustomAuthenticationEntryPoint authenticationEntryPoint;
  private final CustomAccessDeniedHandler accessDeniedHandler;

  /**
   * 헤더 기반 토큰 인증을 위한 보안 정책과 접근 규칙을 설정한다.
   *
   * @param http HTTP 보안 설정을 구성하는 빌더
   * @return 설정이 적용된 보안 필터 체인
   * @throws Exception 보안 필터 체인 구성에 실패한 경우
   */
  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http
  ) throws Exception {
    http
        // 인증 쿠키 없이 요청 헤더로 토큰을 전달하므로 CSRF 보호 비활성화
        .csrf(AbstractHttpConfigurer::disable)

        // 인증 정보를 HTTP 세션에 저장하거나 세션에서 조회하지 않음
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )

        // 직접 구현할 JSON 로그인 API를 사용
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)

        // 인증 실패 및 권한 부족 응답을 공통 오류 형식으로 반환
        .exceptionHandling(exception -> exception
            .authenticationEntryPoint(authenticationEntryPoint)
            .accessDeniedHandler(accessDeniedHandler)
        )

        .authorizeHttpRequests(authorize -> authorize
            // 로그인 없이 접근할 수 있는 회원가입 및 로그인 API
            .requestMatchers(
                HttpMethod.POST,
                "/api/members/signup",
                "/api/auth/login"
            ).permitAll()

            // 로그인 없이 접근할 수 있는 중복 확인 및 조회 API
            .requestMatchers(
                HttpMethod.GET,
                "/api/members/check-email",
                "/api/members/check-nickname",
                "/api/members/{memberId:[0-9]+}",
                "/api/posts"
            ).permitAll()

            // 위 규칙에 해당하지 않는 요청은 인증 필요
            .anyRequest().authenticated()
        );

    return http.build();
  }
}
