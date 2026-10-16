package com.poomitda.global.config;

import com.poomitda.global.security.jwt.JwtProperties;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

  @Bean
  public SecretKey jwtSigningKey(JwtProperties properties) {
    validateExpiration(
        properties.accessTokenExpiration(),
        "jwt.access-token-expiration"
    );
    validateExpiration(
        properties.refreshTokenExpiration(),
        "jwt.refresh-token-expiration"
    );

    byte[] keyBytes;
    try {
      keyBytes = Base64.getDecoder().decode(properties.secret());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "jwt.secret은 올바른 Base64 형식이어야 합니다."
      );
    }

    if (keyBytes.length < 32) {
      throw new IllegalStateException(
          "jwt.secret은 Base64 디코딩 후 32바이트 이상이어야 합니다."
      );
    }

    return Keys.hmacShaKeyFor(keyBytes);
  }

  private void validateExpiration(Duration expiration, String propertyName) {
    if (expiration.isZero() || expiration.isNegative()) {
      throw new IllegalStateException(
          propertyName + "은 0보다 커야 합니다."
      );
    }
  }
}
