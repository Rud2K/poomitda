package com.poomitda.domain.post.validation;

import com.poomitda.domain.post.exception.ImageUploadErrorCode;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ImageFileValidator {

  private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
  private static final long MAX_PIXEL_COUNT = 25_000_000L;

  public ValidatedImage validate(MultipartFile file) {
    ImageFormat expectedFormat = validateBasic(file);
    byte[] bytes = readBytes(file);

    validateImageContent(bytes, expectedFormat);

    return new ValidatedImage(bytes, expectedFormat);
  }

  private ImageFormat validateBasic(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(
          CommonErrorCode.INVALID_INPUT_VALUE,
          "이미지 파일은 필수 입력 항목입니다."
      );
    }

    validateSize(file.getSize());

    return ImageFormat.fromFilename(file.getOriginalFilename())
        .orElseThrow(this::invalidImage);
  }

  private void validateSize(long size) {
    if (size > MAX_FILE_SIZE) {
      throw new BusinessException(
          CommonErrorCode.PAYLOAD_TOO_LARGE,
          "이미지 파일 크기는 5MB 이하여야 합니다."
      );
    }
  }

  private byte[] readBytes(MultipartFile file) {
    try (InputStream input = file.getInputStream()) {
      byte[] bytes = input.readNBytes((int) (MAX_FILE_SIZE + 1));

      validateSize(bytes.length);

      if (bytes.length == 0) {
        throw new BusinessException(
            CommonErrorCode.INVALID_INPUT_VALUE,
            "이미지 파일은 필수 입력 항목입니다."
        );
      }

      return bytes;
    } catch (IOException e) {
      throw new UncheckedIOException("업로드 파일을 읽지 못했습니다.", e);
    }
  }

  private void validateImageContent(byte[] bytes, ImageFormat expectedFormat) {
    try (MemoryCacheImageInputStream input =
        new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);

      if (!readers.hasNext()) {
        throw invalidImage();
      }

      ImageReader reader = readers.next();

      try {
        reader.setInput(input);

        ImageFormat actualFormat = detectFormat(reader);

        if (actualFormat != expectedFormat) {
          throw invalidImage();
        }

        int width = reader.getWidth(0);
        int height = reader.getHeight(0);

        if (width <= 0 || height <= 0) {
          throw invalidImage();
        }

        if ((long) width * height > MAX_PIXEL_COUNT) {
          throw new BusinessException(
              ImageUploadErrorCode.UPLOAD_NOT_ALLOWED,
              "이미지는 2,500만 픽셀 이하여야 합니다."
          );
        }

        BufferedImage image = reader.read(0);

        if (image == null) {
          throw invalidImage();
        }

        image.flush();
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw invalidImage();
    }
  }

  private ImageFormat detectFormat(ImageReader reader) throws IOException {
    return switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
      case "jpg", "jpeg" -> ImageFormat.JPEG;
      case "png" -> ImageFormat.PNG;
      case "webp" -> ImageFormat.WEBP;
      default -> throw invalidImage();
    };
  }

  private BusinessException invalidImage() {
    return new BusinessException(ImageUploadErrorCode.UPLOAD_NOT_ALLOWED);
  }
}
