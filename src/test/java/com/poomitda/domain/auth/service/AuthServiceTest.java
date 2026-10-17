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
import com.poomitda.global.security.jwt.JwtTokenProvider;
import com.poomitda.global.security.util.RefreshTokenHasher;
import java.time.*;
import java.util.Optional;
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
}