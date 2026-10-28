package com.poomitda.domain.post.validation;

import java.util.*;
import lombok.*;

@Getter
@RequiredArgsConstructor
public enum ImageFormat {

  JPEG("jpg", "image/jpeg"),
  PNG("png", "image/png"),
  WEBP("webp", "image/webp");

  private final String extension;
  private final String contentType;

  public static Optional<ImageFormat> fromFilename(String filename) {
    if (filename == null || filename.isBlank()) {
      return Optional.empty();
    }

    int lastDotIndex = filename.lastIndexOf('.');
    if (lastDotIndex < 0 || lastDotIndex == filename.length() - 1) {
      return Optional.empty();
    }

    String extension = filename.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);

    return switch (extension) {
      case "jpg", "jpeg" -> Optional.of(JPEG);
      case "png" -> Optional.of(PNG);
      case "webp" -> Optional.of(WEBP);
      default -> Optional.empty();
    };
  }
}
