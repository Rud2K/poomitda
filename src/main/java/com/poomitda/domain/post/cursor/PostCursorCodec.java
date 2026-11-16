package com.poomitda.domain.post.cursor;

import com.poomitda.domain.post.exception.PostErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class PostCursorCodec {

  private final JsonMapper jsonMapper;

  public String encode(PostCursor cursor) {
    // cursor가 null이면 IllegalArgumentException 발생
    if (cursor == null) {
      throw new IllegalArgumentException("인코딩할 커서는 null일 수 없습니다.");
    }

    // try 안에서 cursor를 JSON 바이트로 변환
    try {
      byte[] jsonBytes = jsonMapper.writeValueAsBytes(cursor);

      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(jsonBytes);
    } catch (JacksonException exception) {
      throw new IllegalStateException(
          "공고 목록 커서 생성에 실패했습니다.",
          exception
      );
    }
  }

  public PostCursor decode(String cursor) {
    // cursor가 null이거나 공백 문자열이면 INVALID_CURSOR 발생
    if (cursor == null || cursor.isBlank()) {
      throw new BusinessException(PostErrorCode.INVALID_CURSOR);
    }

    // try 안에서 URL용 Base64 문자열을 바이트로 복원
    try {
      byte[] jsonBytes = Base64.getUrlDecoder().decode(cursor);

      PostCursor decodedCursor = jsonMapper.readValue(
          jsonBytes,
          PostCursor.class
      );

      if (decodedCursor == null) {
        throw new BusinessException(PostErrorCode.INVALID_CURSOR);
      }

      return decodedCursor;
    } catch (IllegalArgumentException | JacksonException exception) {
      throw new BusinessException(PostErrorCode.INVALID_CURSOR);
    }
  }
}
