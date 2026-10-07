package com.poomitda.global.exception;

import com.poomitda.global.exception.exception.BusinessException;
import com.poomitda.global.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * <p>비즈니스 규칙 위반으로 발생한 예외를 처리한다.</p>
   *
   * <p>호출 상황:</p>
   * <ul>
   *   <li>서비스에서 {@link BusinessException}을 발생시킨 경우</li>
   *   <li>예: 권한 부족, 이메일 중복, 중복 신청</li>
   * </ul>
   *
   * <p>오류 코드에 정의된 HTTP 상태를 사용한다.</p>
   * <p>메시지는 예외에 담긴 기본 메시지 또는 상황별 메시지를 유지한다.</p>
   *
   * @param exception 오류 코드와 메시지가 담긴 비즈니스 예외
   * @return 해당 HTTP 상태와 공통 오류 응답
   */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(
      BusinessException exception
  ) {
    ErrorCode errorCode = exception.getErrorCode();

    ErrorResponse response = ErrorResponse.of(
        errorCode,
        exception.getMessage()
    );

    return ResponseEntity
        .status(errorCode.getHttpStatus())
        .body(response);
  }

  /**
   * <p>요청 DTO의 바인딩 및 검증 실패를 처리한다.</p>
   *
   * <p>호출 상황:</p>
   * <ul>
   *   <li>요청 DTO 처리 중 {@link MethodArgumentNotValidException}이 발생한 경우</li>
   *   <li>예: {@code @Valid} 검증에서 {@code @NotBlank}, {@code @Email} 조건 위반</li>
   * </ul>
   *
   * <p>입력값과 상세 검증 내용은 응답에 노출하지 않는다.</p>
   * <p>컨트롤러 메서드의 검증 선언 방식에 따라 다른 검증 예외가 발생할 수도 있다.</p>
   *
   * @param exception 요청 DTO의 바인딩 및 검증 실패 예외
   * @return HTTP 400 / INVALID_INPUT_VALUE / 기본 메시지
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
      MethodArgumentNotValidException exception
  ) {
    ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

    return ResponseEntity
        .status(errorCode.getHttpStatus())
        .body(ErrorResponse.of(errorCode));
  }

  /**
   * <p>컨트롤러 메서드의 입력값 및 반환값 검증 실패를 처리한다.</p>
   *
   * <p>호출 상황:</p>
   * <ul>
   *   <li>메서드 파라미터에 선언한 {@code @Positive}, {@code @Min} 등의 조건 위반</li>
   *   <li>메서드 반환값에 선언한 검증 조건 위반</li>
   * </ul>
   *
   * <p>처리 기준:</p>
   * <ul>
   *   <li>입력값 오류: HTTP 400 / INVALID_INPUT_VALUE</li>
   *   <li>반환값 오류: HTTP 500 / INTERNAL_SERVER_ERROR</li>
   * </ul>
   *
   * <p>반환값 오류는 문제가 발생한 메서드를 로그에 기록한다.</p>
   *
   * @param exception 메서드 입력값 및 반환값의 검증 실패 예외
   * @return 실패 대상에 따른 HTTP 상태와 공통 오류 응답
   */
  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
      HandlerMethodValidationException exception
  ) {
    ErrorCode errorCode;

    if (exception.isForReturnValue()) {
      log.error("컨트롤러 반환값 검증 실패: {}", exception.getMethod().toGenericString());
      errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
    } else {
      errorCode = ErrorCode.INVALID_INPUT_VALUE;
    }

    return ResponseEntity
        .status(errorCode.getHttpStatus())
        .body(ErrorResponse.of(errorCode));
  }

  /**
   * <p>요청 본문을 읽거나 Java 객체로 변환하지 못한 경우를 처리한다.</p>
   *
   * <p>호출 상황:</p>
   * <ul>
   *   <li>JSON 문법 오류</li>
   *   <li>본문 필드의 자료형 변환 실패</li>
   *   <li>필수 요청 본문 누락</li>
   * </ul>
   *
   * <p>URL 경로 및 쿼리 파라미터의 자료형 변환 오류는 별도로 처리한다.</p>
   * <p>내부 예외 메시지와 원본 요청 데이터는 응답에 노출하지 않는다.</p>
   *
   * @param exception 요청 본문 읽기 및 변환 실패 예외
   * @return HTTP 400 / INVALID_INPUT_VALUE / 기본 메시지
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
      HttpMessageNotReadableException exception
  ) {
    ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

    return ResponseEntity
        .status(errorCode.getHttpStatus())
        .body(ErrorResponse.of(errorCode));
  }

  /**
   * <p>URL 경로 및 쿼리 파라미터의 자료형 변환 실패를 처리한다.</p>
   *
   * <p>호출 상황:</p>
   * <ul>
   *   <li>{@link Long} 타입의 공고 ID에 "abc"를 전달한 경우</li>
   *   <li>숫자형 쿼리 파라미터에 숫자가 아닌 값을 전달한 경우</li>
   * </ul>
   *
   * <p>자료형 변환 후 발생하는 검증 실패와는 구분한다.</p>
   * <p>예를 들어 -1이 {@code @Positive} 조건을 위반한 경우는 검증 오류이다.</p>
   *
   * @param exception 요청 파라미터의 자료형 변환 실패 예외
   * @return HTTP 400 / INVALID_INPUT_VALUE / 기본 메시지
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
      MethodArgumentTypeMismatchException exception
  ) {
    ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

    return ResponseEntity
        .status(errorCode.getHttpStatus())
        .body(ErrorResponse.of(errorCode));
  }

  /**
   * <p>필수 요청 파라미터 누락을 처리한다.</p>
   *
   * <p>호출 상황:</p>
   * <ul>
   *   <li>필수 {@code @RequestParam}을 생략한 경우</li>
   *   <li>값이 변환 과정에서 {@code null}이 되어 필수 조건을 위반한 경우</li>
   * </ul>
   *
   * <p>예: 필수 쿼리 파라미터인 region 없이 요청한 경우</p>
   * <p>JSON 본문 내부의 필드 누락은 별도로 처리한다.</p>
   *
   * @param exception 필수 요청 파라미터 누락 예외
   * @return HTTP 400 / INVALID_INPUT_VALUE / 기본 메시지
   */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
      MissingServletRequestParameterException exception
  ) {
    ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

    return ResponseEntity
        .status(errorCode.getHttpStatus())
        .body(ErrorResponse.of(errorCode));
  }

  /**
   * <p>별도 처리 메서드가 선택되지 않은 예외를 최종 처리한다.</p>
   *
   * <p>기존 처리 흐름에 위임하는 예외:</p>
   * <ul>
   *   <li>Spring ErrorResponse를 구현한 HTTP 예외</li>
   *   <li>Spring Security의 인증·권한 예외</li>
   * </ul>
   *
   * <p>나머지 예외는 원인과 호출 경로를 서버 로그에 기록한다.</p>
   * <p>클라이언트에는 내부 정보 없이 공통 서버 오류를 반환한다.</p>
   * <p>해당 메서드까지 전달되지 않은 필터·별도 스레드의 예외는 처리 대상에 포함되지 않는다.</p>
   *
   * @param exception 별도 처리 메서드가 선택되지 않은 예외
   * @return 직접 처리 시 HTTP 500 / INTERNAL_SERVER_ERROR / 기본 메시지
   * @throws Exception 기존 HTTP 및 보안 처리 흐름에 위임하는 원래 예외
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleException(
      Exception exception
  ) throws Exception {
    // HTTP 상태가 정의된 Spring 예외는 기존 처리 흐름에 위임
    if (exception instanceof org.springframework.web.ErrorResponse) {
      throw exception;
    }

    // 인증/권한 예외는 Spring Security 처리 흐름에 위임
    if (exception instanceof AuthenticationException
        || exception instanceof AccessDeniedException) {
      throw exception;
    }

    log.error("요청 처리 중 예상하지 못한 서버 오류가 발생했습니다.", exception);

    ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

    return ResponseEntity
        .status(errorCode.getHttpStatus())
        .body(ErrorResponse.of(errorCode));
  }
}
