package com.poomitda.domain.post.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.poomitda.domain.post.dto.PostRequestDto.*;
import com.poomitda.domain.post.entity.constant.*;
import com.poomitda.global.constant.*;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class PostCreateValidatorTest {

  private final PostCreateValidator validator = new PostCreateValidator();

  private static final String IMAGE_URL_1 = "https://example.com/posts/image1.png";

  private static final String IMAGE_URL_2 = "https://example.com/posts/image2.png";

  @Test
  @DisplayName("입양 공고의 임시 보호 기간 null -> 검증 통과")
  void acceptAdoptioWithoutFosterPeriod() {
    CreatePostRequest request = createRequest(
        NoticeType.ADOPTION,
        null,
        validImages()
    );

    assertDoesNotThrow(() -> validator.validate(request));
  }

  @Test
  @DisplayName("임시보호 공고의 기간 입력 -> 검증 통과")
  void acceptsFosterWithPeriod() {
    CreatePostRequest request = createRequest(
        NoticeType.FOSTER,
        "3개월",
        validImages()
    );

    assertDoesNotThrow(() -> validator.validate(request));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "   ", "\t", "\n"})
  @DisplayName("임시보호 공고의 기간 null 또는 빈 문자열 또는 공백 -> 입력값 오류")
  void rejectsFosterWithoutPeriod(String fosterPeriod) {
    CreatePostRequest request = createRequest(
        NoticeType.FOSTER,
        fosterPeriod,
        validImages()
    );

    assertInvalidInput(request);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "3개월"})
  @DisplayName("입양 공고의 임시보호 기간 입력 -> 입력값 오류")
  void rejectsAdoptionWithFosterPeriod(String fosterPeriod) {
    CreatePostRequest request = createRequest(
        NoticeType.ADOPTION,
        fosterPeriod,
        validImages()
    );

    assertInvalidInput(request);
  }

  @Test
  @DisplayName("대표 이미지 없음 -> 입력값 오류")
  void rejectsMissingRepresentative() {
    CreatePostRequest request = createRequest(
        NoticeType.ADOPTION,
        null,
        List.of(
            new PostImageRequest(IMAGE_URL_1, false, 0),
            new PostImageRequest(IMAGE_URL_2, false, 1)
        )
    );

    assertInvalidInput(request);
  }

  @Test
  @DisplayName("대표 이미지 두 개 -> 입력값 오류")
  void rejectsMultipleRepresentatives() {
    CreatePostRequest request = createRequest(
        NoticeType.ADOPTION,
        null,
        List.of(
            new PostImageRequest(IMAGE_URL_1, true, 0),
            new PostImageRequest(IMAGE_URL_2, true, 1)
        )
    );

    assertInvalidInput(request);
  }

  @Test
  @DisplayName("이미지 URL 중복 -> 입력값 오류")
  void rejectsDuplicateImageUrls() {
    CreatePostRequest request = createRequest(
        NoticeType.ADOPTION,
        null,
        List.of(
            new PostImageRequest(IMAGE_URL_1, true, 0),
            new PostImageRequest(IMAGE_URL_1, false, 1)
        )
    );

    assertInvalidInput(request);
  }

  @Test
  @DisplayName("이미지 표시 순서 중복 -> 입력값 오류")
  void rejectsDuplicateDisplayOrders() {
    CreatePostRequest request = createRequest(
        NoticeType.ADOPTION,
        null,
        List.of(
            new PostImageRequest(IMAGE_URL_1, true, 0),
            new PostImageRequest(IMAGE_URL_2, false, 0)
        )
    );

    assertInvalidInput(request);
  }

  @Test
  @DisplayName("불연속 표시 순서 및 목록 뒤의 대표 이미지 -> 검증 통과")
  void acceptsNonConsecutiveOrdersAndLaterRepresentative() {
    CreatePostRequest request = createRequest(
        NoticeType.ADOPTION,
        null,
        List.of(
            new PostImageRequest(IMAGE_URL_1, false, 5),
            new PostImageRequest(IMAGE_URL_2, true, 0)
        )
    );

    assertDoesNotThrow(() -> validator.validate(request));
  }

  private List<PostImageRequest> validImages() {
    return List.of(new PostImageRequest(IMAGE_URL_1, true, 0));
  }

  private CreatePostRequest createRequest(
      NoticeType noticeType,
      String fosterPeriod,
      List<PostImageRequest> images
  ) {
    return new CreatePostRequest(
        "구조한 고양이의 가족을 찾습니다.",
        "공고 등록 검증 테스트입니다.",
        noticeType,
        Species.CAT,
        "코리안 숏헤어",
        null,
        Gender.UNKNOWN,
        false,
        fosterPeriod,
        null,
        Region.GYEONGGI,
        "구리시",
        null,
        images
    );
  }

  private void assertInvalidInput(CreatePostRequest request) {
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> validator.validate(request)
    );

    assertEquals(
        CommonErrorCode.INVALID_INPUT_VALUE,
        exception.getErrorCode()
    );
  }
}