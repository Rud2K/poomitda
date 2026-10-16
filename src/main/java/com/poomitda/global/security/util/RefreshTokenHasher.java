package com.poomitda.global.security.util;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenHasher {

  public String hash(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new IllegalArgumentException(
          "Refresh Token은 비어 있을 수 없습니다."
      );
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");

      byte[] hashBytes = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));

      return HexFormat.of().formatHex(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(
          "SHA-256 알고리즘을 사용할 수 없습니다.", e
      );
    }
  }
}
