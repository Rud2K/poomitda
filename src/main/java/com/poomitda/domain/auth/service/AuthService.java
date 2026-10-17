package com.poomitda.domain.auth.service;

import com.poomitda.domain.auth.dto.*;
import com.poomitda.domain.auth.entity.RefreshToken;
import com.poomitda.domain.auth.exception.AuthErrorCode;
import com.poomitda.domain.auth.repository.RefreshTokenRepository;
import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.entity.constant.MemberStatus;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.global.exception.exception.BusinessException;
import com.poomitda.global.security.jwt.JwtTokenProvider;
import com.poomitda.global.security.util.RefreshTokenHasher;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final MemberRepository memberRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenHasher refreshTokenHasher;

  /**
   * 회원 인증 후 토큰을 발급하고 Refresh Token의 해시를 저장한다.
   *
   * @param request 입력값 검증을 완료한 로그인 요청
   * @return 발급한 토큰과 로그인한 회원 정보
   * @throws BusinessException 회원이 없거나, 활동이 불가하거나, 비밀번호가 일치하지 않는 경우
   */
  @Transactional
  public AuthResponseDto.LoginResponse login(
      AuthRequestDto.LoginRequest request
  ) {
    // 이메일로 회원을 조회하며 비관적 락을 획득
    Member member = memberRepository.findByEmailForUpdate(request.email())
        .orElseThrow(() -> new BusinessException(AuthErrorCode.LOGIN_FAILED));

    // 활동 가능한 회원인지 검증
    if (member.getStatus() != MemberStatus.ACTIVE) {
      throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
    }

    // 비밀번호가 일치하는지 검증
    if (!passwordEncoder.matches(request.password(), member.getPassword())) {
      throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
    }

    // Access Token과 Refresh Token 발급
    JwtTokenProvider.IssuedToken accessToken = jwtTokenProvider.createAccessToken(
        member.getMemberId(),
        member.getRole()
    );

    JwtTokenProvider.IssuedToken refreshToken = jwtTokenProvider.createRefreshToken(
        member.getMemberId()
    );

    // Refresh Token의 해시와 만료 시각을 DB에 저장하거나 교체
    String tokenHash = refreshTokenHasher.hash(refreshToken.token());

    LocalDateTime expiresAt = LocalDateTime.ofInstant(
        refreshToken.expiresAt(),
        ZoneOffset.UTC
    );

    refreshTokenRepository.findByMember_MemberId(member.getMemberId())
        .ifPresentOrElse(
            storedToken -> storedToken.rotate(tokenHash, expiresAt),
            () -> refreshTokenRepository.save(
                new RefreshToken(member, tokenHash, expiresAt)
            )
        );

    // 토큰 원문과 회원 정보를 응답 DTO로 반환
    return new AuthResponseDto.LoginResponse(
        accessToken.token(),
        refreshToken.token(),
        member.getMemberId(),
        member.getNickname(),
        member.getRole()
    );
  }
}
