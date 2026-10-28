package com.poomitda.domain.post.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.poomitda.domain.member.entity.constant.MemberRole;
import com.poomitda.domain.member.service.MemberAuthenticationService;
import com.poomitda.domain.post.dto.PostResponseDto;
import com.poomitda.domain.post.service.ImageUploadService;
import com.poomitda.global.security.jwt.JwtTokenProvider;
import com.poomitda.global.security.principal.MemberPrincipal;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:multipart-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none"
    }
)
@ActiveProfiles("test")
class ImageUploadMultipartIntegrationTest {

  private static final int MAX_FILE_SIZE = 5 * 1024 * 1024;

  private static final ParameterizedTypeReference<Map<String, Object>>
      RESPONSE_TYPE = new ParameterizedTypeReference<>() {};

  @LocalServerPort
  private int port;

  @Autowired
  private JwtTokenProvider jwtTokenProvider;

  @MockitoBean
  private ImageUploadService imageUploadService;

  @MockitoBean
  private MemberAuthenticationService memberAuthenticationService;

  @MockitoBean
  private S3Client s3Client;

  private RestClient client;
  private String accessToken;

  @BeforeEach
  void setUp() {
    client = RestClient.builder()
        .baseUrl("http://localhost:" + port)
        // 4xx 응답도 예외로 바꾸지 않고 상태와 본문을 검사한다.
        .defaultStatusHandler(
            HttpStatusCode::isError,
            (request, response) -> {}
        )
        .build();

    when(memberAuthenticationService.authenticate(1L))
        .thenReturn(new MemberPrincipal(1L, MemberRole.ROLE_PROTECTOR));

    accessToken = jwtTokenProvider
        .createAccessToken(1L, MemberRole.ROLE_PROTECTOR)
        .token();
  }

  @Test
  @DisplayName("정확히 5MB인 파일은 서버의 multipart 크기 검사를 통과한다")
  void acceptsFileAtSizeLimit() {
    when(imageUploadService.upload(eq(1L), any(MultipartFile.class)))
        .thenAnswer(invocation -> {
          MultipartFile uploadedFile = invocation.getArgument(1);

          assertEquals(MAX_FILE_SIZE, uploadedFile.getSize());
          assertEquals("photo.png", uploadedFile.getOriginalFilename());

          return new PostResponseDto.ImageUploadResponse(
              "posts/1/example.png",
              "https://example.com/posts/1/example.png"
          );
        });

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", createFile(MAX_FILE_SIZE, "photo.png"));

    ResponseEntity<Map<String, Object>> response = upload(body);

    assertEquals(200, response.getStatusCode().value());
    assertEquals("SUCCESS", requireBody(response).get("code"));

    verify(imageUploadService).upload(eq(1L), any(MultipartFile.class));
    verifyNoInteractions(s3Client);
  }

  @Test
  @DisplayName("5MB보다 1바이트 큰 파일은 413으로 거부한다")
  void rejectsFileOverSizeLimit() {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", createFile(MAX_FILE_SIZE + 1, "photo.png"));

    assertPayloadTooLarge(upload(body));

    verifyNoInteractions(imageUploadService, s3Client);
  }

  @Test
  @DisplayName("각 파일이 5MB 이하여도 요청 전체가 6MB를 초과하면 거부한다")
  void rejectsRequestOverTotalSizeLimit() {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

    int fileSize = 3 * 1024 * 1024 + 1024;

    body.add("file", createFile(fileSize, "photo.png"));
    body.add("extra", createFile(fileSize, "extra.png"));

    assertPayloadTooLarge(upload(body));

    verifyNoInteractions(imageUploadService, s3Client);
  }

  private ResponseEntity<Map<String, Object>> upload(
      MultiValueMap<String, Object> body
  ) {
    return client.post()
        .uri("/api/images")
        .headers(headers -> headers.setBearerAuth(accessToken))
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(body)
        .retrieve()
        .toEntity(RESPONSE_TYPE);
  }

  private ByteArrayResource createFile(int size, String filename) {
    return new ByteArrayResource(new byte[size]) {
      @Override
      public String getFilename() {
        return filename;
      }
    };
  }

  private void assertPayloadTooLarge(ResponseEntity<Map<String, Object>> response) {
    assertEquals(413, response.getStatusCode().value());

    Map<String, Object> body = requireBody(response);

    assertEquals("PAYLOAD_TOO_LARGE", body.get("code"));
    assertEquals("허용된 요청 크기를 초과했습니다.", body.get("message"));
    assertTrue(body.containsKey("data"));
    assertNull(body.get("data"));
  }

  private Map<String, Object> requireBody(ResponseEntity<Map<String, Object>> response) {
    Map<String, Object> body = response.getBody();
    assertNotNull(body);
    return body;
  }
}