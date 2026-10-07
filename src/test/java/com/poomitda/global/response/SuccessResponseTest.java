package com.poomitda.global.response;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class SuccessResponseTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
        .standaloneSetup(new TestController())
        .build();
  }

  // 테스트 전용 응답 DTO
  public record TestResponse(
      Long memberId,
      String nickname
  ) {}

  // 테스트 전용 컨트롤러
  @RestController
  static class TestController {

    @GetMapping("/test/success/data")
    public SuccessResponse<TestResponse> withData() {
      TestResponse data = new TestResponse(1L, "품잇다");
      return SuccessResponse.of("회원 정보 조회 성공", data);
    }

    @GetMapping("/test/success/no-data")
    public SuccessResponse<Void> withoutData() {
      return SuccessResponse.of("성공적으로 로그아웃되었습니다.");
    }
  }

  @Test
  @DisplayName("데이터 있음 -> 성공 응답에 DTO 포함")
  void successResponseWithDataTest() throws Exception {
    // given
    var request = get("/test/success/data");

    // when
    ResultActions result = mockMvc.perform(request);

    // then
    result
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.*", hasSize(3)))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.message").value("회원 정보 조회 성공"))
        .andExpect(jsonPath("$.data").isMap())
        .andExpect(jsonPath("$.data.memberId").value(1))
        .andExpect(jsonPath("$.data.nickname").value("품잇다"));
  }

  @Test
  @DisplayName("데이터 없음 -> data 필드를 null로 유지")
  void successResponseWithoutDataTest() throws Exception {
    // given
    var request = get("/test/success/no-data");

    // when
    ResultActions result = mockMvc.perform(request);

    // then
    result
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.*", hasSize(3)))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.message").value("성공적으로 로그아웃되었습니다."))
        .andExpect(jsonPath("$.data").hasJsonPath())
        .andExpect(jsonPath("$.data").value(nullValue()));
  }
}