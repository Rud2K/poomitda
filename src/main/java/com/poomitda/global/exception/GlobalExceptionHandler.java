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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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
    CommonErrorCode commonErrorCode = CommonErrorCode.INVALID_INPUT_VALUE;

    return ResponseEntity
        .status(commonErrorCode.getHttpStatus())
        .body(ErrorResponse.of(commonErrorCode));
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
    CommonErrorCode commonErrorCode;

    if (exception.isForReturnValue()) {
      log.error("컨트롤러 반환값 검증 실패: {}", exception.getMethod().toGenericString());
      commonErrorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;
    } else {
      commonErrorCode = CommonErrorCode.INVALID_INPUT_VALUE;
    }

    return ResponseEntity
        .status(commonErrorCode.getHttpStatus())
        .body(ErrorResponse.of(commonErrorCode));
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
    CommonErrorCode commonErrorCode = CommonErrorCode.INVALID_INPUT_VALUE;

    return ResponseEntity
        .status(commonErrorCode.getHttpStatus())
        .body(ErrorResponse.of(commonErrorCode));
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
    CommonErrorCode commonErrorCode = CommonErrorCode.INVALID_INPUT_VALUE;

    return ResponseEntity
        .status(commonErrorCode.getHttpStatus())
        .body(ErrorResponse.of(commonErrorCode));
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
    CommonErrorCode commonErrorCode = CommonErrorCode.INVALID_INPUT_VALUE;

    return ResponseEntity
        .status(commonErrorCode.getHttpStatus())
        .body(ErrorResponse.of(commonErrorCode));
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

    CommonErrorCode commonErrorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;

    return ResponseEntity
        .status(commonErrorCode.getHttpStatus())
        .body(ErrorResponse.of(commonErrorCode));
  }

  /**
   * <p>파일 또는 multipart 요청 전체가 허용된 크기를 초과한 경우를 처리한다.</p>
   *
   * <p>호출 상황:</p>
   * <ul>
   *   <li>업로드 파일이 {@code spring.servlet.multipart.max-file-size}를 초과한 경우</li>
   *   <li>multipart 요청 전체가 {@code spring.servlet.multipart.max-request-size}를 초과한 경우</li>
   *   <li>크기 초과가 {@link MaxUploadSizeExceededException}으로 전달된 경우</li>
   * </ul>
   *
   * <p>요청 전체 크기에는 파일 데이터뿐 아니라 multipart 구분자와 각 부분의 헤더 등이 포함된다.</p>
   * <p>서비스의 파일 검증에 도달하기 전 발생한 크기 초과도 공통 오류 형식으로 반환한다.</p>
   * <p>서비스에서 크기 초과를 나타내는 {@link BusinessException}을 발생시킨 경우에는
   * {@link #handleBusinessException(BusinessException)}에서 처리한다.</p>
   *
   * <p>내부 예외 메시지는 응답에 노출하지 않고 오류 코드의 기본 메시지를 사용한다.</p>
   * <p>프록시나 서블릿 컨테이너에서 요청을 직접 거부하여 이 예외 처리기로 전달되지 않은 경우는
   * 처리 대상에 포함되지 않는다.</p>
   *
   * @param exception 파일 또는 multipart 요청 전체의 크기 초과 예외
   * @return HTTP 413 / PAYLOAD_TOO_LARGE / 기본 메시지
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(
      MaxUploadSizeExceededException exception
  ) {
    CommonErrorCode commonErrorCode = CommonErrorCode.PAYLOAD_TOO_LARGE;

    return ResponseEntity
        .status(commonErrorCode.getHttpStatus())
        .body(ErrorResponse.of(commonErrorCode));
  }

  /**
   * <p>multipart 요청에서 필수 요청 부분이 누락된 경우를 처리한다.</p>
   *
   * <p>호출 상황:</p>
   * <ul>
   *   <li>필수 {@code @RequestPart}에 해당하는 요청 부분을 생략한 경우</li>
   *   <li>요청 부분의 이름이 컨트롤러에서 지정한 이름과 일치하지 않는 경우</li>
   *   <li>예: 이미지 업로드 요청에서 {@code file} 부분 없이 전송한 경우</li>
   * </ul>
   *
   * <p>필수 요청 부분을 찾지 못해 {@link MissingServletRequestPartException}이 발생한 경우에 적용한다.</p>
   * <p>파일 부분은 존재하지만 내용이 0바이트인 경우에는 서비스의 파일 검증에서 별도로 처리한다.</p>
   * <p>쿼리 파라미터 누락이나 JSON 본문 내부의 필드 누락은 이 메서드의 처리 대상이 아니다.</p>
   *
   * <p>다른 multipart API에서도 사용할 수 있도록 이미지 전용 문구 대신 공통 오류 코드의
   * 기본 메시지를 반환한다.</p>
   * <p>누락된 요청 부분의 이름과 내부 예외 메시지는 응답에 노출하지 않는다.</p>
   *
   * @param exception multipart 요청의 필수 부분 누락 예외
   * @return HTTP 400 / INVALID_INPUT_VALUE / 기본 메시지
   */
  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<ErrorResponse> handleMissingServletRequestPartException(
      MissingServletRequestPartException exception
  ) {
    CommonErrorCode commonErrorCode = CommonErrorCode.INVALID_INPUT_VALUE;

    return ResponseEntity
        .status(commonErrorCode.getHttpStatus())
        .body(ErrorResponse.of(commonErrorCode));
  }
}
