package com.poomitda.global.exception;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.poomitda.global.exception.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Objects;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;
  private GlobalExceptionHandler exceptionHandler;

  @BeforeEach
  void setup() {
    exceptionHandler = new GlobalExceptionHandler();

    mockMvc = MockMvcBuilders
        .standaloneSetup(new TestController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  // 테스트 전용 요청 DTO
  public record TestRequest(
      @NotBlank String name
  ) {}

  // 테스트 전용 컨트롤러
  @RestController
  static class TestController {

    @GetMapping("/test/errors/business")
    public void business() {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    @GetMapping("/test/errors/business-custom")
    public void businessCustom() {
      throw new BusinessException(
          ErrorCode.FORBIDDEN,
          "해당 공고를 수정할 권한이 없습니다."
      );
    }

    @PostMapping("test/errors/body")
    public void body(@Valid @RequestBody TestRequest request) {
      // 유효한 요청이면 정상 종료
    }

    @GetMapping("/test/errors/positive")
    public void positive(@RequestParam("id") @Positive Long id) {
      // 양수가 아니면 메서드 실행 전에 검증 실패
    }

    @NotNull
    @GetMapping("/test/errors/return-value")
    public String returnValue() {
      // 반환값 검증 실패를 의도적으로 발생
      return null;
    }

    @GetMapping("/test/errors/posts/{postId}")
    public void post(@PathVariable("postId") Long postId) {
      // 숫자로 변환할 수 없으면 메서드 실행 전에 실패
    }

    @GetMapping("/test/errors/required")
    public void required(@RequestParam("region") String region) {
      // 필수 파라미터가 없으면 메서드 실행 전에 실패
    }

    @GetMapping("/test/errors/unexpected")
    public void unexpected() {
      throw new IllegalStateException("테스트용 내부 오류 상세");
    }

    @GetMapping("/test/errors/not-found")
    public void notFound() {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }

  @Test
  @DisplayName("비즈니스 예외 -> 기본 오류 응답")
  void handleBusinessExceptionTest() throws Exception {
    // given
    var request = get("/test/errors/business");

    // when
    ResultActions result = mockMvc.perform(request);

    // then
    assertErrorResponse(
        result,
        403,
        "FORBIDDEN",
        "해당 요청을 수행할 권한이 없습니다."
    );
    assertResolvedException(result, BusinessException.class);
  }

  @Test
  @DisplayName("비즈니스 예외 -> 지정 메시지 유지")
  void handleBusinessExceptionCustomMessageTest() throws Exception {
    // given
    var request = get("/test/errors/business-custom");

    // when
    ResultActions result = mockMvc.perform(request);

    // then
    assertErrorResponse(
        result,
        403,
        "FORBIDDEN",
        "해당 공고를 수정할 권한이 없습니다."
    );
    assertResolvedException(result, BusinessException.class);
  }

  @Test
  @DisplayName("DTO 검증 실패 -> 400")
  void handleMethodArgumentNotValidExceptionTest() throws Exception {
    // given
    var request = post("/test/errors/body")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "name": ""
            }
            """);

    // when
    ResultActions result = mockMvc.perform(request);

    // then
    assertInvalidInputResponse(result);
    assertResolvedException(result, MethodArgumentNotValidException.class);
  }

  @Test
  @DisplayName("파라미터 검증 실패 -> 400")
  void handleHandlerMethodValidationExceptionTest() throws Exception {
    // given
    var request = get("/test/errors/positive")
        .param("id", "-1");

    // when
    ResultActions result = mockMvc.perform(request);

    // then
    assertInvalidInputResponse(result);

    HandlerMethodValidationException exception = assertInstanceOf(
        HandlerMethodValidationException.class,
        result.andReturn().getResolvedException()
    );
    assertFalse(Objects.requireNonNull(exception).isForReturnValue());
  }

  @Test
  @DisplayName("반환값 검증 실패 -> 500")
  void handleHandlerMethodReturnValueValidationExceptionTest() throws Exception {
    // given
    var request = get("/test/errors/return-value");

    // when
    ResultActions result = mockMvc.perform(request);

    // then
    assertInternalServerErrorResponse(result);

    HandlerMethodValidationException exception = assertInstanceOf(
        HandlerMethodValidationException.class,
        result.andReturn().getResolvedException()
    );
    assertTrue(Objects.requireNonNull(exception).isForReturnValue());
  }

  @Test
  @DisplayName("JSON 문법 오류 -> 400")
  void handleHttpMessageNotReadableExceptionTest() throws Exception {
    // given
    var request = post("/test/errors/body")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{");

    // when
    ResultActions result = mockMvc.perform(request);

    // then
    assertInvalidInputResponse(result);
    assertResolvedException(result, HttpMessageNotReadableException.class);
  }

  @Test
  @DisplayName("요청 본문 누락 -> 400")
  void handleMissingRequestBodyTest() throws Exception {
    // given
    var request = post("/test/errors/body")
        .contentType(MediaType.APPLICATION_JSON);

    // when
    ResultActions result = mockMvc.perform(request);

    // then
    assertInvalidInputResponse(result);
    assertResolvedException(result, HttpMessageNotReadableException.class);
  }

  @Test
  @DisplayName("파라미터 타입 불일치 -> 400")
  void handleMethodArgumentTypeMismatchExceptionTest() throws Exception {
    // given
    var request = get("/test/errors/posts/abc");

    // when
    ResultActions result = mockMvc.perform(request);

    // then
    assertInvalidInputResponse(result);
    assertResolvedException(result, MethodArgumentTypeMismatchException.class);
  }

  @Test
  @DisplayName("필수 파라미터 누락 -> 400")
  void handleMissingServletRequestParameterExceptionTest() throws Exception {
    // given
    var request = get("/test/errors/required");

    // when
    ResultActions result = mockMvc.perform(request);

    // then
    assertInvalidInputResponse(result);
    assertResolvedException(result, MissingServletRequestParameterException.class);
  }

  @Test
  @DisplayName("예상치 못한 오류 -> 공통 500 응답")
  void handleExceptionTest() throws Exception {
    // given
    var request = get("/test/errors/unexpected");

    // when
    ResultActions result = mockMvc.perform(request);

    // then
    assertInternalServerErrorResponse(result);
    assertResolvedException(result, IllegalStateException.class);
  }

  @Test
  @DisplayName("HTTP 예외 -> 404 유지")
  void preserveHttpStatusTest() throws Exception {
    // given
    var request = get("/test/errors/not-found");

    // when
    ResultActions result = mockMvc.perform(request);

    // then
    result.andExpect(status().isNotFound());
    assertResolvedException(result, ResponseStatusException.class);
  }

  @Test
  @DisplayName("미지원 HTTP 메서드 -> 405·Allow 헤더 유지")
  void preserveMethodNotAllowedTest() throws Exception {
    // given
    var request = post("/test/errors/business");

    // when
    ResultActions result = mockMvc.perform(request);

    // then
    result
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string("Allow", containsString("GET")));
    assertResolvedException(result, HttpRequestMethodNotSupportedException.class);
  }

  @Test
  @DisplayName("인증 예외 -> 기존 예외 재전달")
  void rethrowAuthenticationExceptionTest() throws Exception {
    // given
    var exception = new BadCredentialsException("테스트용 인증 실패");

    // when
    BadCredentialsException thrown = assertThrows(
        BadCredentialsException.class,
        () -> exceptionHandler.handleException(exception)
    );

    // then
    assertSame(exception, thrown);
  }

  @Test
  @DisplayName("권한 예외 -> 기존 예외 재전달")
  void rethrowAccessDeniedExceptionTest() throws Exception {
    // given
    var exception = new AccessDeniedException("테스트용 접근 거부");

    // when
    AccessDeniedException thrown = assertThrows(
        AccessDeniedException.class,
        () -> exceptionHandler.handleException(exception)
    );

    // then
    assertSame(exception, thrown);
  }

  // HTTP 상태와 공통 오류 응답의 구조 및 값 검증
  private void assertErrorResponse(
      ResultActions result,
      int expectedStatus,
      String expectedCode,
      String expectedMessage
  ) throws Exception {
    result
        .andExpect(status().is(expectedStatus))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.*", hasSize(3)))
        .andExpect(jsonPath("$.code").value(expectedCode))
        .andExpect(jsonPath("$.message").value(expectedMessage))
        .andExpect(jsonPath("$.data").hasJsonPath())
        .andExpect(jsonPath("$.data").value(nullValue()));
  }

  // 반복되는 400 응답 검증
  private void assertInvalidInputResponse(ResultActions result) throws Exception {
    assertErrorResponse(
        result,
        400,
        "INVALID_INPUT_VALUE",
        "입력값이 올바르지 않습니다."
    );
  }

  // 반복되는 500 응답 검증
  private void assertInternalServerErrorResponse(ResultActions result) throws Exception {
    assertErrorResponse(
        result,
        500,
        "INTERNAL_SERVER_ERROR",
        "서버 내부 오류가 발생했습니다."
    );
  }

  // 예상한 종류의 예외가 실제로 발생했는지 검증
  private void assertResolvedException(
      ResultActions result,
      Class<? extends Exception> expectedType
  ) {
    assertInstanceOf(
        expectedType,
        result.andReturn().getResolvedException()
    );
  }
}