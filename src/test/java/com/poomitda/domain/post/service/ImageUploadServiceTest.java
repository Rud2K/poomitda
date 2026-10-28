package com.poomitda.domain.post.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.poomitda.domain.member.entity.constant.MemberRole;
import com.poomitda.domain.member.service.MemberAuthenticationService;
import com.poomitda.domain.post.validation.*;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import com.poomitda.global.security.principal.MemberPrincipal;
import com.poomitda.global.storage.S3ImageStorage;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.mock.web.MockMultipartFile;

class ImageUploadServiceTest {

  private final MemberAuthenticationService authenticationService = mock(MemberAuthenticationService.class);

  private final ImageFileValidator validator = mock(ImageFileValidator.class);

  private final S3ImageStorage storage = mock(S3ImageStorage.class);

  private final ImageUploadHistoryService historyService = mock(ImageUploadHistoryService.class);

  private final ImageUploadService service = new ImageUploadService(
      authenticationService,
      validator,
      storage,
      historyService
  );

  private final MockMultipartFile file = new MockMultipartFile(
      "file",
      "photo.png",
      "image/png",
      new byte[] {1, 2, 3}
  );

  private final byte[] validatedBytes = {4, 5, 6};

  private static final String IMAGE_URL = "https://poomitda-dev-images.s3.ap-northeast-2.amazonaws.com/posts/test.png";

  @BeforeEach
  void setUp() {
    when(authenticationService.authenticate(1L))
        .thenReturn(new MemberPrincipal(1L, MemberRole.ROLE_PROTECTOR));

    when(validator.validate(file))
        .thenReturn(new ValidatedImage(validatedBytes, ImageFormat.PNG));

    when(storage.getImageUrl(anyString()))
        .thenReturn(IMAGE_URL);
  }

  @Test
  @DisplayName("검증된 이미지를 업로드하고 이력을 저장한 뒤 키와 URL을 반환한다")
  void uploadsImageAndSavesHistory() {
    var response = service.upload(1L, file);

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

    InOrder order = inOrder(storage, historyService);

    order.verify(storage).upload(
        keyCaptor.capture(),
        same(validatedBytes),
        eq("image/png")
    );

    String imageKey = keyCaptor.getValue();

    order.verify(historyService).savePending(
        1L,
        imageKey,
        IMAGE_URL
    );

    assertTrue(imageKey.matches(
        "posts/1/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
            + "[0-9a-f]{4}-[0-9a-f]{12}\\.png"
    ));
    assertEquals(imageKey, response.imageKey());
    assertEquals(IMAGE_URL, response.imageUrl());

    verify(storage).getImageUrl(imageKey);
    verify(storage, never()).delete(anyString());
  }

  @Test
  @DisplayName("보호자 권한이 없으면 파일 검증과 업로드를 진행하지 않는다")
  void rejectsNonProtector() {
    when(authenticationService.authenticate(1L))
        .thenReturn(new MemberPrincipal(1L, MemberRole.ROLE_USER));

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.upload(1L, file)
    );

    assertEquals(CommonErrorCode.FORBIDDEN, exception.getErrorCode());
    verifyNoInteractions(validator, storage, historyService);
  }

  @Test
  @DisplayName("파일 검증에 실패하면 S3와 DB에 저장하지 않는다")
  void stopsWhenValidationFails() {
    BusinessException failure = new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);

    when(validator.validate(file)).thenThrow(failure);

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.upload(1L, file)
    );

    assertSame(failure, exception);
    verifyNoInteractions(storage, historyService);
  }

  @Test
  @DisplayName("S3 업로드에 실패하면 이력을 저장하지 않는다")
  void stopsWhenUploadFails() {
    RuntimeException failure = new RuntimeException("테스트용 S3 업로드 실패");

    doThrow(failure).when(storage).upload(anyString(), any(byte[].class), anyString());

    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> service.upload(1L, file)
    );

    assertSame(failure, exception);
    verifyNoInteractions(historyService);
    verify(storage, never()).delete(anyString());
  }

  @Test
  @DisplayName("이력 저장에 실패하면 업로드한 이미지를 삭제한다")
  void deletesImageWhenHistorySaveFails() {
    RuntimeException failure = new RuntimeException("테스트용 DB 저장 실패");

    doThrow(failure).when(historyService).savePending(eq(1L), anyString(), eq(IMAGE_URL));

    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> service.upload(1L, file)
    );

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

    InOrder order = inOrder(storage, historyService);

    order.verify(storage).upload(
        keyCaptor.capture(),
        same(validatedBytes),
        eq("image/png")
    );

    String imageKey = keyCaptor.getValue();

    order.verify(historyService).savePending(1L, imageKey, IMAGE_URL);
    order.verify(storage).delete(imageKey);

    assertSame(failure, exception);
  }

  @Test
  @DisplayName("이미지 정리에도 실패하면 원래 오류를 유지하고 삭제 오류를 보존한다")
  void preservesOriginalFailureWhenCleanupFails() {
    RuntimeException saveFailure = new RuntimeException("테스트용 DB 저장 실패");

    RuntimeException cleanupFailure = new RuntimeException("테스트용 S3 삭제 실패");

    doThrow(saveFailure).when(historyService).savePending(eq(1L), anyString(), eq(IMAGE_URL));

    doThrow(cleanupFailure).when(storage).delete(anyString());

    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> service.upload(1L, file)
    );

    assertSame(saveFailure, exception);
    assertEquals(1, exception.getSuppressed().length);
    assertSame(cleanupFailure, exception.getSuppressed()[0]);
  }
}