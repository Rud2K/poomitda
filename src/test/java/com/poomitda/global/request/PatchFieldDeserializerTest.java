package com.poomitda.global.request;

import static org.junit.jupiter.api.Assertions.*;

import com.poomitda.global.constant.Region;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;

class PatchFieldDeserializerTest {

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  record TestRequest(
      PatchField<String> nickname,
      PatchField<Region> preferredRegion
  ) {}

  @Test
  @DisplayName("필드 생략 -> 전달되지 않은 상태로 변환")
  void deserializeAbsentFieldsTest() {
    // given
    String json = "{}";

    // when
    TestRequest request = jsonMapper.readValue(json, TestRequest.class);

    // then
    assertNotNull(request.nickname());
    assertFalse(request.nickname().present());
    assertNull(request.nickname().value());

    assertNotNull(request.preferredRegion());
    assertFalse(request.preferredRegion().present());
    assertNull(request.preferredRegion().value());
  }

  @Test
  @DisplayName("명시적 null -> null이 전달된 상태로 변환")
  void deserializeExplicitNullFieldsTest() {
    // given
    String json = """
        {
          "nickname": null,
          "preferredRegion": null
        }
        """;

    // when
    TestRequest request = jsonMapper.readValue(json, TestRequest.class);

    // then
    assertNotNull(request.nickname());
    assertTrue(request.nickname().present());
    assertNull(request.nickname().value());

    assertNotNull(request.preferredRegion());
    assertTrue(request.preferredRegion().present());
    assertNull(request.preferredRegion().value());
  }

  @Test
  @DisplayName("문자열과 enum 전달 -> 각 내부 타입으로 변환")
  void deserializeSuppliedValuesTest() {
    // given
    String json = """
        {
          "nickname": "새닉네임",
          "preferredRegion": "GYEONGGI"
        }
        """;

    // when
    TestRequest request = jsonMapper.readValue(json, TestRequest.class);

    // then
    assertNotNull(request.nickname());
    assertTrue(request.nickname().present());
    assertEquals("새닉네임", request.nickname().value());

    assertNotNull(request.preferredRegion());
    assertTrue(request.preferredRegion().present());
    assertEquals(Region.GYEONGGI, request.preferredRegion().value());
  }

  @Test
  @DisplayName("일부 필드만 전달 -> 전달된 필드와 생략된 필드를 구분")
  void deserializePartiallySuppliedFieldsTest() {
    // given
    String json = """
        {
          "nickname": "새닉네임"
        }
        """;

    // when
    TestRequest request = jsonMapper.readValue(json, TestRequest.class);

    // then
    assertNotNull(request.nickname());
    assertTrue(request.nickname().present());
    assertEquals("새닉네임", request.nickname().value());

    assertNotNull(request.preferredRegion());
    assertFalse(request.preferredRegion().present());
    assertNull(request.preferredRegion().value());
  }
}