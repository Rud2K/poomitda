package com.poomitda.domain.matching.cursor;

import com.poomitda.domain.matching.exception.ApplicationErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class MyApplicationCursorCodec {

  private final JsonMapper jsonMapper;

  /**
   * 신청 목록 커서를 URL에서 사용할 수 있는 문자열로 변환한다.
   *
   * @param cursor 문자열로 변환할 커서
   * @return JSON 바이트를 URL용 Base64로 인코딩한 문자열
   * @throws IllegalArgumentException 커서가 null인 경우
   * @throws IllegalStateException JSON 변환에 실패한 경우
   */
  public String encode(MyApplicationCursor cursor) {
    if (cursor == null) {
      throw new IllegalArgumentException("인코딩할 커서는 null일 수 없습니다.");
    }

    try {
      byte[] jsonBytes = jsonMapper.writeValueAsBytes(cursor);

      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(jsonBytes);
    } catch (JacksonException exception) {
      throw new IllegalStateException(
          "내 신청 목록 커서 생성에 실패했습니다.",
          exception
      );
    }
  }

  /**
   * URL용 Base64 문자열을 신청 목록 커서로 복원한다.
   *
   * @param cursor 요청으로 전달받은 커서 문자열
   * @return 복원된 커서
   * @throws BusinessException 문자열이 비어 있거나 커서로 복원할 수 없는 경우
   */
  public MyApplicationCursor decode(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      throw new BusinessException(ApplicationErrorCode.INVALID_CURSOR);
    }

    try {
      byte[] jsonBytes = Base64.getUrlDecoder().decode(cursor);

      MyApplicationCursor decodedCursor = jsonMapper.readValue(
          jsonBytes,
          MyApplicationCursor.class
      );

      if (decodedCursor == null) {
        throw new BusinessException(ApplicationErrorCode.INVALID_CURSOR);
      }

      return decodedCursor;
    } catch (IllegalArgumentException | JacksonException exception) {
      throw new BusinessException(ApplicationErrorCode.INVALID_CURSOR);
    }
  }
}
