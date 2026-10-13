package com.poomitda.global.security.handler;

import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.response.ErrorResponse;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final JsonMapper jsonMapper;

  /**
   * 인증이 필요한 요청에 대해 공통 형식의 401 오류 응답을 작성한다.
   *
   * @param request 인증이 필요한 HTTP 요청
   * @param response 오류 응답을 작성할 HTTP 응답
   * @param authException 처리를 유발한 인증 예외
   * @throws IOException 응답 출력 중 입출력 오류가 발생한 경우
   */
  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException
  ) throws IOException {
    CommonErrorCode errorCode = CommonErrorCode.UNAUTHORIZED;

    response.setStatus(errorCode.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");

    jsonMapper.writeValue(
        response.getWriter(),
        ErrorResponse.of(errorCode)
    );
  }
}
