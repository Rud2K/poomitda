package com.poomitda.domain.post.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.poomitda.domain.member.entity.constant.MemberRole;
import com.poomitda.domain.member.service.MemberAuthenticationService;
import com.poomitda.domain.post.dto.PostResponseDto;
import com.poomitda.domain.post.service.ImageUploadService;
import com.poomitda.global.config.SecurityConfig;
import com.poomitda.global.exception.GlobalExceptionHandler;
import com.poomitda.global.security.handler.*;
import com.poomitda.global.security.jwt.JwtTokenProvider;
import com.poomitda.global.security.principal.MemberPrincipal;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.multipart.MultipartFile;

@WebMvcTest(ImageUploadController.class)
@Import({
    SecurityConfig.class,
    CustomAuthenticationEntryPoint.class,
    CustomAccessDeniedHandler.class,
    GlobalExceptionHandler.class
})
class ImageUploadControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ImageUploadService imageUploadService;

  @MockitoBean
  private JwtTokenProvider jwtTokenProvider;

  @MockitoBean
  private MemberAuthenticationService memberAuthenticationService;

  private static final String IMAGE_KEY = "posts/1/example.png";

  private static final String IMAGE_URL =
      "https://poomitda-dev-images.s3.ap-northeast-2.amazonaws.com/"
          + IMAGE_KEY;

  @Test
  @DisplayName("보호자의 이미지 업로드 요청 -> 공통 200 응답")
  void uploadsImage() throws Exception {
    MockMultipartFile file = createFile();

    when(imageUploadService.upload(eq(1L), any(MultipartFile.class)))
        .thenReturn(new PostResponseDto.ImageUploadResponse(
            IMAGE_KEY,
            IMAGE_URL
        ));

    mockMvc.perform(multipart("/api/images")
            .file(file)
            .with(asMember(MemberRole.ROLE_PROTECTOR))
        )
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.message").value("이미지 업로드 처리 성공"))
        .andExpect(jsonPath("$.data.imageKey").value(IMAGE_KEY))
        .andExpect(jsonPath("$.data.imageUrl").value(IMAGE_URL));

    verify(imageUploadService).upload(eq(1L), same(file));
  }

  @Test
  @DisplayName("인증되지 않은 이미지 업로드 요청 -> 401")
  void rejectsUnauthenticatedRequest() throws Exception {
    mockMvc.perform(multipart("/api/images")
            .file(createFile())
        )
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    verifyNoInteractions(imageUploadService);
  }

  @Test
  @DisplayName("일반 회원의 이미지 업로드 요청 -> 403")
  void rejectsRegularMember() throws Exception {
    mockMvc.perform(multipart("/api/images")
            .file(createFile())
            .with(asMember(MemberRole.ROLE_USER))
        )
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    verifyNoInteractions(imageUploadService);
  }

  @Test
  @DisplayName("관리자의 이미지 업로드 요청 -> 403")
  void rejectsAdmin() throws Exception {
    mockMvc.perform(multipart("/api/images")
            .file(createFile())
            .with(asMember(MemberRole.ROLE_ADMIN))
        )
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    verifyNoInteractions(imageUploadService);
  }

  @Test
  @DisplayName("보호자의 요청에서 필수 파일 누락 -> 400")
  void rejectsMissingFile() throws Exception {
    mockMvc.perform(multipart("/api/images")
            .with(asMember(MemberRole.ROLE_PROTECTOR))
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
        .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));

    verifyNoInteractions(imageUploadService);
  }

  private MockMultipartFile createFile() {
    return new MockMultipartFile(
        "file",
        "photo.png",
        "image/png",
        new byte[] {1, 2, 3}
    );
  }

  private RequestPostProcessor asMember(MemberRole role) {
    MemberPrincipal principal = new MemberPrincipal(1L, role);

    var authenticationToken =
        UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            List.of(new SimpleGrantedAuthority(role.name()))
        );

    return authentication(authenticationToken);
  }
}