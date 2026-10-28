package com.poomitda.domain.post.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.poomitda.domain.post.exception.ImageUploadErrorCode;
import com.poomitda.global.exception.*;
import com.poomitda.global.exception.exception.BusinessException;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;

class ImageFileValidatorTest {

  private static final int MAX_FILE_SIZE = 5 * 1024 * 1024;

  private final ImageFileValidator validator = new ImageFileValidator();

  @Test
  @DisplayName("정상 PNG 이미지는 검증을 통과하고 원본 바이트를 반환한다")
  void acceptsPng() throws IOException {
    byte[] bytes = createImage("png");
    MockMultipartFile file = file("photo.png", "image/png", bytes);

    ValidatedImage result = validator.validate(file);

    assertEquals(ImageFormat.PNG, result.format());
    assertArrayEquals(bytes, result.bytes());
  }

  @Test
  @DisplayName("대문자 JPEG 확장자도 허용한다")
  void acceptsUppercaseJpegExtension() throws IOException {
    byte[] bytes = createImage("jpeg");
    MockMultipartFile file = file("photo.JPEG", "image/jpeg", bytes);

    ValidatedImage result = validator.validate(file);

    assertEquals(ImageFormat.JPEG, result.format());
    assertArrayEquals(bytes, result.bytes());
  }

  @Test
  @DisplayName("클라이언트의 콘텐츠 타입 대신 실제 이미지 형식을 사용한다")
  void detectsFormatFromContent() throws IOException {
    MockMultipartFile file = file(
        "photo.png",
        "application/octet-stream",
        createImage("png")
    );

    ValidatedImage result = validator.validate(file);

    assertEquals(ImageFormat.PNG, result.format());
    assertEquals("image/png", result.format().getContentType());
  }

  @Test
  @DisplayName("파일이 없으면 입력값 오류를 반환한다")
  void rejectsNullFile() {
    assertError(
        CommonErrorCode.INVALID_INPUT_VALUE,
        () -> validator.validate(null)
    );
  }

  @Test
  @DisplayName("빈 파일이면 입력값 오류를 반환한다")
  void rejectsEmptyFile() {
    MockMultipartFile file = file("photo.png", "image/png", new byte[0]);

    assertError(
        CommonErrorCode.INVALID_INPUT_VALUE,
        () -> validator.validate(file)
    );
  }

  @Test
  @DisplayName("5MB를 1바이트 초과하면 용량 초과 오류를 반환한다")
  void rejectsOversizedFile() {
    MockMultipartFile file = file(
        "photo.png",
        "image/png",
        new byte[MAX_FILE_SIZE + 1]
    );

    assertError(
        CommonErrorCode.PAYLOAD_TOO_LARGE,
        () -> validator.validate(file)
    );
  }

  @Test
  @DisplayName("허용되지 않은 확장자는 실제 이미지여도 거절한다")
  void rejectsUnsupportedExtension() throws IOException {
    MockMultipartFile file = file(
        "photo.gif",
        "image/png",
        createImage("png")
    );

    assertError(
        ImageUploadErrorCode.UPLOAD_NOT_ALLOWED,
        () -> validator.validate(file)
    );
  }

  @Test
  @DisplayName("확장자와 실제 이미지 형식이 다르면 거절한다")
  void rejectsFormatMismatch() throws IOException {
    MockMultipartFile file = file(
        "photo.jpg",
        "image/jpeg",
        createImage("png")
    );

    assertError(
        ImageUploadErrorCode.UPLOAD_NOT_ALLOWED,
        () -> validator.validate(file)
    );
  }

  @Test
  @DisplayName("이미지 확장자를 가진 텍스트 파일은 거절한다")
  void rejectsTextDisguisedAsImage() {
    MockMultipartFile file = file(
        "photo.png",
        "image/png",
        "이 파일은 이미지가 아닙니다.".getBytes(StandardCharsets.UTF_8)
    );

    assertError(
        ImageUploadErrorCode.UPLOAD_NOT_ALLOWED,
        () -> validator.validate(file)
    );
  }

  @Test
  @DisplayName("신고된 크기가 작아도 실제 데이터가 5MB를 초과하면 거절한다")
  void rejectsActualContentExceedingLimit() {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "photo.png",
        "image/png",
        new byte[MAX_FILE_SIZE + 1]
    ) {
      @Override
      public long getSize() {
        return 1;
      }
    };

    assertError(
        CommonErrorCode.PAYLOAD_TOO_LARGE,
        () -> validator.validate(file)
    );
  }

  @Test
  @DisplayName("PNG 식별 정보만 있고 이미지 데이터가 없으면 거절한다")
  void rejectsTruncatedPng() throws IOException {
    byte[] validPng = createImage("png");
    byte[] truncatedPng = Arrays.copyOf(validPng, 8);

    MockMultipartFile file = file(
        "photo.png",
        "image/png",
        truncatedPng
    );

    assertError(
        ImageUploadErrorCode.UPLOAD_NOT_ALLOWED,
        () -> validator.validate(file)
    );
  }

  @Test
  @DisplayName("파일 읽기 실패는 원인을 보존한 서버 측 예외로 전달한다")
  void preservesFileReadFailure() {
    IOException cause = new IOException("테스트용 파일 읽기 실패");

    MockMultipartFile file = new MockMultipartFile(
        "file",
        "photo.png",
        "image/png",
        new byte[] {1}
    ) {
      @Override
      public @NonNull InputStream getInputStream() throws IOException {
        throw cause;
      }
    };

    UncheckedIOException exception = assertThrows(
        UncheckedIOException.class,
        () -> validator.validate(file)
    );

    assertSame(cause, exception.getCause());
  }

  @Test
  @DisplayName("정상 WebP 이미지는 검증을 통과한다")
  void acceptsWebp() throws IOException {
    byte[] bytes = readSampleWebp();
    MockMultipartFile file = file(
        "sample.webp",
        "image/webp",
        bytes
    );

    ValidatedImage result = validator.validate(file);

    assertEquals(ImageFormat.WEBP, result.format());
    assertEquals("image/webp", result.format().getContentType());
    assertArrayEquals(bytes, result.bytes());
  }

  @Test
  @DisplayName("WebP 데이터를 PNG 확장자로 보내면 거절한다")
  void rejectsWebpWithPngExtension() throws IOException {
    MockMultipartFile file = file(
        "sample.png",
        "image/png",
        readSampleWebp()
    );

    assertError(
        ImageUploadErrorCode.UPLOAD_NOT_ALLOWED,
        () -> validator.validate(file)
    );
  }

  @Test
  @DisplayName("정확히 5MB인 정상 PNG는 허용한다")
  void acceptsPngAtFileSizeLimit() throws IOException {
    byte[] bytes = createPngWithExactSize(MAX_FILE_SIZE);

    assertEquals(MAX_FILE_SIZE, bytes.length);

    ValidatedImage result = validator.validate(
        file("boundary.png", "image/png", bytes)
    );

    assertEquals(ImageFormat.PNG, result.format());
    assertArrayEquals(bytes, result.bytes());
  }

  @Test
  @DisplayName("정확히 2,500만 픽셀인 PNG는 허용한다")
  void acceptsPngAtPixelLimit() throws IOException {
    byte[] bytes = createBinaryPng(5_000, 5_000);

    assertTrue(bytes.length < MAX_FILE_SIZE);

    ValidatedImage result = validator.validate(
        file("boundary.png", "image/png", bytes)
    );

    assertEquals(ImageFormat.PNG, result.format());
  }

  @Test
  @DisplayName("2,500만 픽셀을 초과한 PNG는 거절한다")
  void rejectsPngAbovePixelLimit() throws IOException {
    byte[] bytes = createBinaryPng(5_001, 5_000);

    assertTrue(bytes.length < MAX_FILE_SIZE);

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> validator.validate(
            file("oversized.png", "image/png", bytes)
        )
    );

    assertEquals(
        ImageUploadErrorCode.UPLOAD_NOT_ALLOWED,
        exception.getErrorCode()
    );
    assertEquals(
        "이미지는 2,500만 픽셀 이하여야 합니다.",
        exception.getMessage()
    );
  }

  private byte[] createImage(String format) throws IOException {
    BufferedImage image = new BufferedImage(
        2,
        2,
        BufferedImage.TYPE_INT_RGB
    );

    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      assertTrue(ImageIO.write(image, format, output));
      return output.toByteArray();
    } finally {
      image.flush();
    }
  }

  private MockMultipartFile file(String filename, String contentType, byte[] bytes) {
    return new MockMultipartFile(
        "file",
        filename,
        contentType,
        bytes
    );
  }

  private byte[] readSampleWebp() throws IOException {
    try (InputStream input = getClass().getResourceAsStream(
        "/images/sample.webp"
    )) {
      assertNotNull(
          input,
          "src/test/resources/images/sample.webp 파일이 필요합니다."
      );

      return input.readAllBytes();
    }
  }

  private byte[] createBinaryPng(int width, int height) throws IOException {
    BufferedImage image = new BufferedImage(
        width,
        height,
        BufferedImage.TYPE_BYTE_BINARY
    );

    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      assertTrue(ImageIO.write(image, "png", output));
      return output.toByteArray();
    } finally {
      image.flush();
    }
  }

  private byte[] createPngWithExactSize(int targetSize) throws IOException {
    byte[] png = createImage("png");

    // PNG 서명 8바이트 + 첫 IHDR 청크 25바이트
    int insertionOffset = 33;

    // 청크의 길이·종류·CRC 필드가 차지하는 공간은 총 12바이트
    int textDataLength = targetSize - png.length - 12;
    byte[] keyword = "Comment".getBytes(StandardCharsets.ISO_8859_1);

    if (textDataLength < keyword.length + 1) {
      throw new IllegalArgumentException("목표 파일 크기가 너무 작습니다.");
    }

    byte[] textData = new byte[textDataLength];
    Arrays.fill(textData, (byte) 'x');
    System.arraycopy(keyword, 0, textData, 0, keyword.length);

    // 키워드와 본문을 구분하는 null 문자
    textData[keyword.length] = 0;

    byte[] chunkType = "tEXt".getBytes(StandardCharsets.US_ASCII);

    CRC32 crc = new CRC32();
    crc.update(chunkType);
    crc.update(textData);

    try (ByteArrayOutputStream output = new ByteArrayOutputStream(targetSize);
        DataOutputStream data = new DataOutputStream(output)) {

      data.write(png, 0, insertionOffset);

      data.writeInt(textData.length);
      data.write(chunkType);
      data.write(textData);
      data.writeInt((int) crc.getValue());

      data.write(
          png,
          insertionOffset,
          png.length - insertionOffset
      );

      data.flush();
      return output.toByteArray();
    }
  }

  private void assertError(ErrorCode expected, Runnable action) {
    BusinessException exception = assertThrows(
        BusinessException.class,
        action::run
    );

    assertEquals(expected, exception.getErrorCode());
  }
}