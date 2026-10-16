package com.poomitda.global.security.jwt;

import com.poomitda.domain.member.entity.constant.MemberRole;
import io.jsonwebtoken.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

  private final JwtProperties jwtProperties;
  private final SecretKey jwtSigningKey;

  // 회원 ID와 권한을 포함한 Access Token을 발급한다.
  public IssuedToken createAccessToken(Long memberId, MemberRole role) {
    return createToken(
        memberId,
        "ACCESS",
        role,
        jwtProperties.accessTokenExpiration()
    );
  }

  // 토큰 재발급에 사용할 Refresh Token을 발급한다.
  public IssuedToken createRefreshToken(Long memberId) {
    return createToken(
        memberId,
        "REFRESH",
        null,
        jwtProperties.refreshTokenExpiration()
    );
  }

  // Access Token을 검증하고 클레임을 반환한다.
  public Claims validateAccessToken(String token) {
    Claims claims = validateToken(token, "ACCESS");

    String role = claims.get("role", String.class);
    if (role == null) {
      throw new JwtException("토큰에 회원 권한이 없습니다.");
    }

    try {
      MemberRole.valueOf(role);
    } catch (IllegalArgumentException e) {
      throw new JwtException("토큰의 회원 권한이 올바르지 않습니다.");
    }

    return claims;
  }

  // Refresh Token을 검증하고 클레임을 반환한다.
  public Claims validateRefreshToken(String token) {
    return validateToken(token, "REFRESH");
  }

  private IssuedToken createToken(
      Long memberId,
      String tokenType,
      MemberRole role,
      Duration expiration
  ) {
    Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Instant expiresAt = issuedAt.plus(expiration).truncatedTo(ChronoUnit.SECONDS);

    JwtBuilder builder = Jwts.builder()
        .issuer(jwtProperties.issuer())
        .subject(memberId.toString())
        .id(UUID.randomUUID().toString())
        .issuedAt(Date.from(issuedAt))
        .expiration(Date.from(expiresAt))
        .claim("tokenType", tokenType);

    if (role != null) {
      builder.claim("role", role.name());
    }

    String token = builder
        .signWith(jwtSigningKey, Jwts.SIG.HS256)
        .compact();

    return new IssuedToken(token, expiresAt);
  }

  private Claims validateToken(String token, String expectedTokenType) {
    if (token == null || token.isBlank()) {
      throw new JwtException("토큰이 없습니다.");
    }

    Claims claims = Jwts.parser()
        .verifyWith(jwtSigningKey)
        .sig()
        .clear()
        .add(Jwts.SIG.HS256)
        .and()
        .requireIssuer(jwtProperties.issuer())
        .require("tokenType", expectedTokenType)
        .build()
        .parseSignedClaims(token)
        .getPayload();

    if (claims.getExpiration() == null) {
      throw new JwtException("토큰에 만료 시각이 없습니다.");
    }

    if (!claims.getExpiration().toInstant().isAfter(Instant.now())) {
      throw new JwtException("토큰이 만료되었습니다.");
    }

    String subject = claims.getSubject();
    if (subject == null || subject.isBlank()) {
      throw new JwtException("토큰에 회원 ID가 없습니다.");
    }

    try {
      long memberId = Long.parseLong(subject);
      if (memberId <= 0) {
        throw new JwtException("토큰의 회원 ID가 올바르지 않습니다.");
      }
    } catch (NumberFormatException e) {
      throw new JwtException("토큰의 회원 ID가 올바르지 않습니다.");
    }

    return claims;
  }

  // 발급한 토큰 문자열과 해당 토큰의 만료 시각
  public record IssuedToken(
      String token,
      Instant expiresAt
  ) {}
}
