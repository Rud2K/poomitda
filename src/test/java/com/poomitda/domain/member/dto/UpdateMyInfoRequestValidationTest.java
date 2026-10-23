package com.poomitda.domain.member.dto;

import static org.junit.jupiter.api.Assertions.*;

import com.poomitda.domain.member.dto.MemberRequestDto.UpdateMyInfoRequest;
import jakarta.validation.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;

class UpdateMyInfoRequestValidationTest {

  private static ValidatorFactory validatorFactory;
  private static Validator validator;

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  @BeforeAll
  static void setUpValidator() {
    validatorFactory = Validation.buildDefaultValidatorFactory();
    validator = validatorFactory.getValidator();
  }

  @AfterAll
  static void closeValidatorFactory() {
    if (validatorFactory != null) {
      validatorFactory.close();
    }
  }

  @Test
  @DisplayName("닉네임 생략 -> 검증 통과")
  void absentNicknameTest() {
    // given
    UpdateMyInfoRequest request = jsonMapper.readValue(
        "{}",
        UpdateMyInfoRequest.class
    );

    // when
    var violations = validator.validate(request);

    // then
    assertNotNull(request.nickname());
    assertFalse(request.nickname().present());
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("닉네임에 null 또는 공백 전달 -> NotBlank 검증 실패")
  void invalidNicknameTest() {
    String[] requests = {
        """
        {"nickname": null}
        """,
        """
        {"nickname": ""}
        """,
        """
        {"nickname": "   "}
        """
    };

    for (String json : requests) {
      // given
      UpdateMyInfoRequest request = jsonMapper.readValue(
          json,
          UpdateMyInfoRequest.class
      );

      // when
      var violations = validator.validate(request);

      // then
      assertTrue(request.nickname().present());
      assertEquals(1, violations.size(), json);

      var violation = violations.iterator().next();

      assertEquals("nickname", violation.getPropertyPath().toString());
      assertEquals(
          NotBlank.class,
          violation.getConstraintDescriptor()
              .getAnnotation()
              .annotationType()
      );
    }
  }

  @Test
  @DisplayName("닉네임 50자 전달 -> 검증 통과")
  void nicknameAtMaxLengthTest() {
    // given
    String nickname = "가".repeat(50);
    String json = """
        {"nickname": "%s"}
        """.formatted(nickname);

    UpdateMyInfoRequest request = jsonMapper.readValue(
        json,
        UpdateMyInfoRequest.class
    );

    // when
    var violations = validator.validate(request);

    // then
    assertEquals(nickname, request.nickname().value());
    assertTrue(violations.isEmpty());
  }

  @Test
  @DisplayName("닉네임 51자 전달 -> Size 검증 실패")
  void nicknameOverMaxLengthTest() {
    // given
    String json = """
        {"nickname": "%s"}
        """.formatted("가".repeat(51));

    UpdateMyInfoRequest request = jsonMapper.readValue(
        json,
        UpdateMyInfoRequest.class
    );

    // when
    var violations = validator.validate(request);

    // then
    assertEquals(1, violations.size());

    var violation = violations.iterator().next();

    assertEquals("nickname", violation.getPropertyPath().toString());
    assertEquals(
        Size.class,
        violation
            .getConstraintDescriptor()
            .getAnnotation()
            .annotationType()
    );
  }
}
