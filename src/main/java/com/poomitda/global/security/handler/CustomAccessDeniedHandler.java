package com.poomitda.global.security.handler;

import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.response.ErrorResponse;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

  private final JsonMapper jsonMapper;

  /**
   * 인증된 사용자의 권한이 부족한 경우 공통 형식의 403 오류 응답을 반환한다.
   *
   * @param request 접근이 거부된 HTTP 요청
   * @param response 오류 응답을 작성할 HTTP 응답
   * @param accessDeniedException 처리를 유발한 접근 거부 예외
   * @throws IOException 응답 출력 중 입출력 오류가 발생한 경우
   */
  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException
  ) throws IOException {
    CommonErrorCode errorCode = CommonErrorCode.FORBIDDEN;

    response.setStatus(errorCode.getHttpStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    jsonMapper.writeValue(
        response.getWriter(),
        ErrorResponse.of(errorCode)
    );
  }
}
