package com.poomitda.domain.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.poomitda.domain.auth.dto.*;
import com.poomitda.domain.auth.entity.RefreshToken;
import com.poomitda.domain.auth.exception.AuthErrorCode;
import com.poomitda.domain.auth.repository.RefreshTokenRepository;
import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.entity.constant.*;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.global.exception.exception.BusinessException;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.security.jwt.*;
import com.poomitda.global.security.util.RefreshTokenHasher;
import io.jsonwebtoken.*;
import java.time.*;
import java.util.*;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock
  private MemberRepository memberRepository;

  @Mock
  private RefreshTokenRepository refreshTokenRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private JwtTokenProvider jwtTokenProvider;

  @Mock
  private RefreshTokenHasher refreshTokenHasher;

  @InjectMocks
  private AuthService authService;

  @Test
  @DisplayName("로그아웃 -> 회원 잠금 후 저장된 Refresh Token 삭제")
  void logoutDeletesStoredTokenTest() {
    // given
    Member member = mock(Member.class);

    when(member.getMemberId()).thenReturn(1L);
    when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);

    RefreshToken storedToken = new RefreshToken(
        member, "a".repeat(64), LocalDateTime.now(ZoneOffset.UTC).plusDays(1)
    );

    when(memberRepository.findByIdForUpdate(1L))
        .thenReturn(Optional.of(member));
    when(refreshTokenRepository.findByMember_MemberId(1L))
        .thenReturn(Optional.of(storedToken));

    // when
    authService.logout(1L);

    // then
    InOrder order = inOrder(memberRepository, refreshTokenRepository);

    order.verify(memberRepository).findByIdForUpdate(1L);
    order.verify(refreshTokenRepository).findByMember_MemberId(1L);
    order.verify(refreshTokenRepository).delete(storedToken);

    verifyNoMoreInteractions(refreshTokenRepository);
    verifyNoInteractions(passwordEncoder, jwtTokenProvider, refreshTokenHasher);
  }

  @Test
  @DisplayName("저장된 Refresh Token 없음 -> 로그아웃 성공")
  void logoutWithoutStoredTokenTest() {
    // given
    Member member = mock(Member.class);

    when(member.getMemberId()).thenReturn(1L);
    when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
    when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
    when(refreshTokenRepository.findByMember_MemberId(1L)).thenReturn(Optional.empty());

    // when & then
    assertDoesNotThrow(() -> authService.logout(1L));

    verify(refreshTokenRepository).findByMember_MemberId(1L);
    verifyNoMoreInteractions(refreshTokenRepository);
    verifyNoInteractions(passwordEncoder, jwtTokenProvider, refreshTokenHasher);
  }

  @Test
  @DisplayName("존재하지 않는 회원 -> 로그아웃 거절")
  void logoutMemberNotFoundTest() {
    // given
    when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

    // when
    BusinessException exception = assertThrows(
        BusinessException.class, () -> authService.logout(1L)
    );

    // then
    assertEquals(CommonErrorCode.UNAUTHORIZED, exception.getErrorCode());
    verifyNoInteractions(
        refreshTokenRepository, passwordEncoder, jwtTokenProvider, refreshTokenHasher
    );
  }

  @Test
  @DisplayName("탈퇴 회원 -> 로그아웃 거절")
  void logoutDeletedMemberTest() {
    // given
    Member member = mock(Member.class);

    when(member.getStatus()).thenReturn(MemberStatus.DELETED);
    when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));

    // when
    BusinessException exception = assertThrows(
        BusinessException.class, () -> authService.logout(1L)
    );

    // then
    assertEquals(CommonErrorCode.UNAUTHORIZED, exception.getErrorCode());
    verifyNoInteractions(
        refreshTokenRepository, passwordEncoder, jwtTokenProvider, refreshTokenHasher
    );
  }

  private Member createActiveMember() {
    Member member = mock(Member.class);

    when(member.getMemberId()).thenReturn(1L);
    when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
    when(member.getPassword()).thenReturn("encodedPassword");
    when(member.getNickname()).thenReturn("품잇다");
    when(member.getRole()).thenReturn(MemberRole.ROLE_USER);

    return member;
  }

  @Test
  @DisplayName("존재하지 않는 회원 -> 로그인 실패")
  void loginMemberNotFoundTest() {
    // given
    AuthRequestDto.LoginRequest request = new AuthRequestDto.LoginRequest(
        "member@example.com",
        "Password1234!"
    );

    when(memberRepository.findByEmailForUpdate(request.email()))
        .thenReturn(Optional.empty());

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> authService.login(request)
    );

    // then
    assertEquals(AuthErrorCode.LOGIN_FAILED, exception.getErrorCode());

    verifyNoInteractions(
        passwordEncoder,
        jwtTokenProvider,
        refreshTokenHasher,
        refreshTokenRepository
    );
  }

  @Test
  @DisplayName("탈퇴 회원 -> 로그인 실패")
  void loginDeletedMemberTest() {
    // given
    AuthRequestDto.LoginRequest request = new AuthRequestDto.LoginRequest(
        "member@example.com",
        "Password1234!"
    );

    Member member = mock(Member.class);
    when(member.getStatus()).thenReturn(MemberStatus.DELETED);

    when(memberRepository.findByEmailForUpdate(request.email()))
        .thenReturn(Optional.of(member));

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> authService.login(request)
    );

    // then
    assertEquals(AuthErrorCode.LOGIN_FAILED, exception.getErrorCode());

    verifyNoInteractions(
        passwordEncoder,
        jwtTokenProvider,
        refreshTokenHasher,
        refreshTokenRepository
    );
  }

  @Test
  @DisplayName("비밀번호 불일치 -> 로그인 실패")
  void loginPasswordMismatchTest() {
    // given
    AuthRequestDto.LoginRequest request = new AuthRequestDto.LoginRequest(
        "member@example.com",
        "Password1234!"
    );

    Member member = mock(Member.class);
    when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
    when(member.getPassword()).thenReturn("encodedPassword");

    when(memberRepository.findByEmailForUpdate(request.email()))
        .thenReturn(Optional.of(member));

    when(passwordEncoder.matches(request.password(), "encodedPassword"))
        .thenReturn(false);

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> authService.login(request)
    );

    // then
    assertEquals(AuthErrorCode.LOGIN_FAILED, exception.getErrorCode());

    verify(passwordEncoder).matches(request.password(), "encodedPassword");

    verifyNoInteractions(
        jwtTokenProvider,
        refreshTokenHasher,
        refreshTokenRepository
    );
  }

  @Test
  @DisplayName("최초 로그인 -> Refresh Token 해시 저장 및 토큰 반환")
  void loginFirstSuccessTest() {
    // given
    AuthRequestDto.LoginRequest request = new AuthRequestDto.LoginRequest(
        "member@example.com",
        "Password1234!"
    );

    Member member = createActiveMember();

    JwtTokenProvider.IssuedToken accessToken =
        new JwtTokenProvider.IssuedToken(
            "access-token",
            Instant.parse("2026-01-01T07:30:00Z")
        );

    JwtTokenProvider.IssuedToken refreshToken =
        new JwtTokenProvider.IssuedToken(
            "refresh-token",
            Instant.parse("2026-01-01T07:00:00Z")
        );

    String tokenHash = "a".repeat(64);
    LocalDateTime expectedExpiresAt =
        LocalDateTime.of(2026, 1, 1, 7, 0);

    when(memberRepository.findByEmailForUpdate(request.email()))
        .thenReturn(Optional.of(member));

    when(passwordEncoder.matches(request.password(), "encodedPassword"))
        .thenReturn(true);

    when(jwtTokenProvider.createAccessToken(1L, MemberRole.ROLE_USER))
        .thenReturn(accessToken);

    when(jwtTokenProvider.createRefreshToken(1L))
        .thenReturn(refreshToken);

    when(refreshTokenHasher.hash(refreshToken.token()))
        .thenReturn(tokenHash);

    when(refreshTokenRepository.findByMember_MemberId(1L))
        .thenReturn(Optional.empty());

    // when
    AuthResponseDto.LoginResponse response = authService.login(request);

    // then
    ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);

    verify(refreshTokenRepository).save(tokenCaptor.capture());

    RefreshToken savedToken = tokenCaptor.getValue();

    assertAll(
        () -> assertSame(member, savedToken.getMember()),
        () -> assertEquals(tokenHash, savedToken.getTokenHash()),
        () -> assertEquals(expectedExpiresAt, savedToken.getExpiresAt()),
        () -> assertEquals(accessToken.token(), response.accessToken()),
        () -> assertEquals(refreshToken.token(), response.refreshToken()),
        () -> assertEquals(Long.valueOf(1L), response.memberId()),
        () -> assertEquals("품잇다", response.nickname()),
        () -> assertEquals(MemberRole.ROLE_USER, response.role())
    );

    verify(passwordEncoder).matches(request.password(), "encodedPassword");
    verify(refreshTokenHasher).hash(refreshToken.token());
  }

  @Test
  @DisplayName("재로그인 -> 기존 Refresh Token 해시 교체 및 새 토큰 반환")
  void loginAgainSuccessTest() {
    // given
    AuthRequestDto.LoginRequest request = new AuthRequestDto.LoginRequest(
        "member@example.com",
        "Password1234!"
    );

    Member member = createActiveMember();

    RefreshToken storedToken = new RefreshToken(
        member,
        "a".repeat(64),
        LocalDateTime.of(2026, 1, 1, 12, 30)
    );

    JwtTokenProvider.IssuedToken accessToken =
        new JwtTokenProvider.IssuedToken(
            "new-access-token",
            Instant.parse("2026-01-01T07:30:00Z")
        );

    JwtTokenProvider.IssuedToken refreshToken =
        new JwtTokenProvider.IssuedToken(
            "new-refresh-token",
            Instant.parse("2026-01-14T07:00:00Z")
        );

    String newTokenHash = "b".repeat(64);
    LocalDateTime expectedExpiresAt =
        LocalDateTime.of(2026, 1, 14, 7, 0);

    when(memberRepository.findByEmailForUpdate(request.email()))
        .thenReturn(Optional.of(member));

    when(passwordEncoder.matches(request.password(), "encodedPassword"))
        .thenReturn(true);

    when(jwtTokenProvider.createAccessToken(1L, MemberRole.ROLE_USER))
        .thenReturn(accessToken);

    when(jwtTokenProvider.createRefreshToken(1L))
        .thenReturn(refreshToken);

    when(refreshTokenHasher.hash(refreshToken.token()))
        .thenReturn(newTokenHash);

    when(refreshTokenRepository.findByMember_MemberId(1L))
        .thenReturn(Optional.of(storedToken));

    // when
    AuthResponseDto.LoginResponse response = authService.login(request);

    // then
    assertAll(
        () -> assertSame(member, storedToken.getMember()),
        () -> assertEquals(newTokenHash, storedToken.getTokenHash()),
        () -> assertEquals(expectedExpiresAt, storedToken.getExpiresAt()),
        () -> assertEquals(accessToken.token(), response.accessToken()),
        () -> assertEquals(refreshToken.token(), response.refreshToken()),
        () -> assertEquals(Long.valueOf(1L), response.memberId()),
        () -> assertEquals("품잇다", response.nickname()),
        () -> assertEquals(MemberRole.ROLE_USER, response.role())
    );

    verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    verify(passwordEncoder).matches(request.password(), "encodedPassword");
    verify(refreshTokenHasher).hash(refreshToken.token());
  }

  @Test
  @DisplayName("Refresh Token 검증 실패 -> 재발급 중단")
  void reissueInvalidTokenTest() {
    // given
    String refreshToken = "invalid-refresh-token";

    when(jwtTokenProvider.validateRefreshToken(refreshToken))
        .thenThrow(new JwtException("테스트용 토큰 검증 실패"));

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> authService.reissue(refreshToken)
    );

    // then
    assertEquals(
        AuthErrorCode.INVALID_REFRESH_TOKEN,
        exception.getErrorCode()
    );

    verify(jwtTokenProvider).validateRefreshToken(refreshToken);
    verifyNoMoreInteractions(jwtTokenProvider);

    verifyNoInteractions(
        memberRepository,
        refreshTokenRepository,
        refreshTokenHasher,
        passwordEncoder
    );
  }

  @Test
  @DisplayName("존재하지 않는 회원 -> 재발급 중단")
  void reissueMemberNotFoundTest() {
    // given
    String refreshToken = "refresh-token";

    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn("1");

    when(jwtTokenProvider.validateRefreshToken(refreshToken))
        .thenReturn(claims);

    when(memberRepository.findByIdForUpdate(1L))
        .thenReturn(Optional.empty());

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> authService.reissue(refreshToken)
    );

    // then
    assertEquals(
        AuthErrorCode.INVALID_REFRESH_TOKEN,
        exception.getErrorCode()
    );

    verify(memberRepository).findByIdForUpdate(1L);
    verify(jwtTokenProvider).validateRefreshToken(refreshToken);
    verifyNoMoreInteractions(jwtTokenProvider);

    verifyNoInteractions(
        refreshTokenRepository,
        refreshTokenHasher,
        passwordEncoder
    );
  }

  @Test
  @DisplayName("탈퇴 회원 -> 재발급 중단")
  void reissueDeletedMemberTest() {
    // given
    String refreshToken = "refresh-token";

    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn("1");

    Member member = mock(Member.class);
    when(member.getStatus()).thenReturn(MemberStatus.DELETED);

    when(jwtTokenProvider.validateRefreshToken(refreshToken))
        .thenReturn(claims);

    when(memberRepository.findByIdForUpdate(1L))
        .thenReturn(Optional.of(member));

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> authService.reissue(refreshToken)
    );

    // then
    assertEquals(
        AuthErrorCode.INVALID_REFRESH_TOKEN,
        exception.getErrorCode()
    );

    verify(memberRepository).findByIdForUpdate(1L);
    verify(jwtTokenProvider).validateRefreshToken(refreshToken);
    verifyNoMoreInteractions(jwtTokenProvider);

    verifyNoInteractions(
        refreshTokenRepository,
        refreshTokenHasher,
        passwordEncoder
    );
  }

  @Test
  @DisplayName("저장된 Refresh Token 없음 -> 재발급 중단")
  void reissueStoredTokenNotFoundTest() {
    // given
    String refreshToken = "refresh-token";

    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn("1");

    Member member = mock(Member.class);
    when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);

    when(jwtTokenProvider.validateRefreshToken(refreshToken))
        .thenReturn(claims);

    when(memberRepository.findByIdForUpdate(1L))
        .thenReturn(Optional.of(member));

    when(refreshTokenRepository.findByMember_MemberId(1L))
        .thenReturn(Optional.empty());

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> authService.reissue(refreshToken)
    );

    // then
    assertEquals(
        AuthErrorCode.INVALID_REFRESH_TOKEN,
        exception.getErrorCode()
    );

    verify(memberRepository).findByIdForUpdate(1L);

    verify(refreshTokenRepository).findByMember_MemberId(1L);
    verifyNoMoreInteractions(refreshTokenRepository);

    verify(jwtTokenProvider).validateRefreshToken(refreshToken);
    verifyNoMoreInteractions(jwtTokenProvider);

    verifyNoInteractions(refreshTokenHasher, passwordEncoder);
  }

  @Test
  @DisplayName("Refresh Token 해시 불일치 -> 재발급 중단")
  void reissueTokenHashMismatchTest() {
    // given
    String refreshToken = "old-refresh-token";
    String requestedTokenHash = "a".repeat(64);
    String storedTokenHash = "b".repeat(64);

    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn("1");

    Member member = mock(Member.class);
    when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);

    LocalDateTime expiresAt =
        LocalDateTime.now(ZoneOffset.UTC).plusDays(1);

    RefreshToken storedToken = new RefreshToken(
        member,
        storedTokenHash,
        expiresAt
    );

    when(jwtTokenProvider.validateRefreshToken(refreshToken))
        .thenReturn(claims);

    when(memberRepository.findByIdForUpdate(1L))
        .thenReturn(Optional.of(member));

    when(refreshTokenRepository.findByMember_MemberId(1L))
        .thenReturn(Optional.of(storedToken));

    when(refreshTokenHasher.hash(refreshToken))
        .thenReturn(requestedTokenHash);

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> authService.reissue(refreshToken)
    );

    // then
    assertEquals(
        AuthErrorCode.INVALID_REFRESH_TOKEN,
        exception.getErrorCode()
    );

    assertAll(
        () -> assertEquals(storedTokenHash, storedToken.getTokenHash()),
        () -> assertEquals(expiresAt, storedToken.getExpiresAt())
    );

    verify(jwtTokenProvider).validateRefreshToken(refreshToken);
    verifyNoMoreInteractions(jwtTokenProvider);

    verify(refreshTokenHasher).hash(refreshToken);
    verifyNoMoreInteractions(refreshTokenHasher);

    verify(refreshTokenRepository).findByMember_MemberId(1L);
    verifyNoMoreInteractions(refreshTokenRepository);

    verifyNoInteractions(passwordEncoder);
  }

  @Test
  @DisplayName("DB의 Refresh Token 만료 -> 재발급 중단")
  void reissueStoredTokenExpiredTest() {
    // given
    String refreshToken = "refresh-token";
    String tokenHash = "a".repeat(64);

    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn("1");

    Member member = mock(Member.class);
    when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);

    LocalDateTime expiredAt =
        LocalDateTime.now(ZoneOffset.UTC).minusDays(1);

    RefreshToken storedToken = new RefreshToken(
        member,
        tokenHash,
        expiredAt
    );

    when(jwtTokenProvider.validateRefreshToken(refreshToken))
        .thenReturn(claims);

    when(memberRepository.findByIdForUpdate(1L))
        .thenReturn(Optional.of(member));

    when(refreshTokenRepository.findByMember_MemberId(1L))
        .thenReturn(Optional.of(storedToken));

    when(refreshTokenHasher.hash(refreshToken))
        .thenReturn(tokenHash);

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> authService.reissue(refreshToken)
    );

    // then
    assertEquals(
        AuthErrorCode.INVALID_REFRESH_TOKEN,
        exception.getErrorCode()
    );

    assertAll(
        () -> assertEquals(tokenHash, storedToken.getTokenHash()),
        () -> assertEquals(expiredAt, storedToken.getExpiresAt())
    );

    verify(jwtTokenProvider).validateRefreshToken(refreshToken);
    verifyNoMoreInteractions(jwtTokenProvider);

    verify(refreshTokenHasher).hash(refreshToken);
    verifyNoMoreInteractions(refreshTokenHasher);

    verify(refreshTokenRepository).findByMember_MemberId(1L);
    verifyNoMoreInteractions(refreshTokenRepository);

    verifyNoInteractions(passwordEncoder);
  }

  @Test
  @DisplayName("정상 재발급 -> 토큰 해시 교체 및 새 토큰 반환")
  void reissueSuccessTest() {
    // given
    String refreshToken = "old-refresh-token";
    String oldTokenHash = "a".repeat(64);
    String newTokenHash = "b".repeat(64);

    Claims claims = mock(Claims.class);
    when(claims.getSubject()).thenReturn("1");

    Member member = mock(Member.class);
    when(member.getMemberId()).thenReturn(1L);
    when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
    when(member.getRole()).thenReturn(MemberRole.ROLE_USER);

    RefreshToken storedToken = new RefreshToken(
        member,
        oldTokenHash,
        LocalDateTime.now(ZoneOffset.UTC).plusDays(1)
    );

    Instant issuedAt = Instant.now()
        .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

    JwtTokenProvider.IssuedToken newAccessToken =
        new JwtTokenProvider.IssuedToken(
            "new-access-token",
            issuedAt.plus(Duration.ofMinutes(30))
        );

    JwtTokenProvider.IssuedToken newRefreshToken =
        new JwtTokenProvider.IssuedToken(
            "new-refresh-token",
            issuedAt.plus(Duration.ofDays(14))
        );

    LocalDateTime expectedExpiresAt = LocalDateTime.ofInstant(
        newRefreshToken.expiresAt(),
        ZoneOffset.UTC
    );

    when(jwtTokenProvider.validateRefreshToken(refreshToken))
        .thenReturn(claims);

    when(memberRepository.findByIdForUpdate(1L))
        .thenReturn(Optional.of(member));

    when(refreshTokenRepository.findByMember_MemberId(1L))
        .thenReturn(Optional.of(storedToken));

    when(refreshTokenHasher.hash(refreshToken))
        .thenReturn(oldTokenHash);

    when(jwtTokenProvider.createAccessToken(1L, MemberRole.ROLE_USER))
        .thenReturn(newAccessToken);

    when(jwtTokenProvider.createRefreshToken(1L))
        .thenReturn(newRefreshToken);

    when(refreshTokenHasher.hash(newRefreshToken.token()))
        .thenReturn(newTokenHash);

    // when
    AuthResponseDto.ReissueResponse response =
        authService.reissue(refreshToken);

    // then
    assertAll(
        () -> assertEquals(newAccessToken.token(), response.accessToken()),
        () -> assertEquals(newRefreshToken.token(), response.refreshToken()),
        () -> assertSame(member, storedToken.getMember()),
        () -> assertEquals(newTokenHash, storedToken.getTokenHash()),
        () -> assertEquals(expectedExpiresAt, storedToken.getExpiresAt())
    );

    verify(jwtTokenProvider).createAccessToken(1L, MemberRole.ROLE_USER);
    verify(jwtTokenProvider).createRefreshToken(1L);
    verify(refreshTokenHasher).hash(refreshToken);
    verify(refreshTokenHasher).hash(newRefreshToken.token());

    verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    verifyNoInteractions(passwordEncoder);
  }

  @Test
  @DisplayName("실제 Refresh Token 서명 변조 -> 재발급 중단")
  void reissueTamperedJwtTest() {
    // given
    SecretKey signingKey = Jwts.SIG.HS256.key().build();
    JwtTokenProvider realTokenProvider = createRealTokenProvider(signingKey);
    AuthService service = createServiceWithRealTokenProvider(realTokenProvider);

    String originalToken = realTokenProvider.createRefreshToken(1L).token();

    // 변조 전 원본은 정상적으로 검증되는지 확인
    assertDoesNotThrow(
        () -> realTokenProvider.validateRefreshToken(originalToken)
    );

    String[] parts = originalToken.split("\\.");
    char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';

    String tamperedToken =
        parts[0] + "." + parts[1] + "." + replacement + parts[2].substring(1);

    // 서명 오류로 검출되는지 확인
    assertThrows(
        io.jsonwebtoken.security.SignatureException.class,
        () -> realTokenProvider.validateRefreshToken(tamperedToken)
    );

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.reissue(tamperedToken)
    );

    // then
    assertEquals(
        AuthErrorCode.INVALID_REFRESH_TOKEN,
        exception.getErrorCode()
    );

    verifyNoInteractions(
        memberRepository,
        refreshTokenRepository,
        refreshTokenHasher,
        passwordEncoder
    );
  }

  @Test
  @DisplayName("실제 Refresh Token JWT 만료 -> 재발급 중단")
  void reissueExpiredJwtTest() {
    // given
    SecretKey signingKey = Jwts.SIG.HS256.key().build();
    JwtTokenProvider realTokenProvider = createRealTokenProvider(signingKey);
    AuthService service = createServiceWithRealTokenProvider(realTokenProvider);

    Instant now = Instant.now();

    String expiredToken = Jwts.builder()
        .issuer("poomitda-test")
        .subject("1")
        .issuedAt(Date.from(now.minus(Duration.ofHours(2))))
        .expiration(Date.from(now.minus(Duration.ofHours(1))))
        .claim("tokenType", "REFRESH")
        .signWith(signingKey, Jwts.SIG.HS256)
        .compact();

    // 서명 오류가 아닌 만료 오류로 검출되는지 확인
    assertThrows(
        ExpiredJwtException.class,
        () -> realTokenProvider.validateRefreshToken(expiredToken)
    );

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.reissue(expiredToken)
    );

    // then
    assertEquals(
        AuthErrorCode.INVALID_REFRESH_TOKEN,
        exception.getErrorCode()
    );

    verifyNoInteractions(
        memberRepository,
        refreshTokenRepository,
        refreshTokenHasher,
        passwordEncoder
    );
  }

  private JwtTokenProvider createRealTokenProvider(SecretKey signingKey) {
    JwtProperties properties = new JwtProperties(
        "poomitda-test",
        Base64.getEncoder().encodeToString(signingKey.getEncoded()),
        Duration.ofMinutes(30),
        Duration.ofDays(14)
    );
    return new JwtTokenProvider(properties, signingKey);
  }

  private AuthService createServiceWithRealTokenProvider(JwtTokenProvider realTokenProvider) {
    return new AuthService(
        memberRepository,
        refreshTokenRepository,
        passwordEncoder,
        realTokenProvider,
        refreshTokenHasher
    );
  }
}
