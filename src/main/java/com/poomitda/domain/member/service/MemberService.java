package com.poomitda.domain.member.service;

import com.poomitda.domain.member.dto.*;
import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.exception.*;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.global.exception.exception.BusinessException;
import java.math.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberService {

  private final MemberRepository memberRepository;
  private final PasswordEncoder passwordEncoder;

  /**
   * 회원가입 정보를 바탕으로 회원을 생성하고 저장한다.
   *
   * @param request 회원가입 요청 정보
   * @return 저장된 회원의 ID, 이메일, 닉네임을 담은 {@link MemberResponseDto.SignupResponse}
   * @throws BusinessException 이메일 또는 닉네임이 중복된 경우
   * @throws DataIntegrityViolationException 중복 오류로 분류할 수 없는 데이터 무결성 위반이 발생한 경우
   */
  @Transactional
  public MemberResponseDto.SignupResponse signup(
      MemberRequestDto.SignupRequest request
  ) {
    // 탈퇴 회원을 포함한 이메일 중복 확인
    if (memberRepository.existsByEmail(request.email())) {
      throw new BusinessException(MemberErrorCode.DUPLICATE_EMAIL);
    }

    // 탈퇴 회원을 포함한 닉네임 중복 확인
    if (memberRepository.existsByNickname(request.nickname())) {
      throw new BusinessException(MemberErrorCode.DUPLICATE_NICKNAME);
    }

    // 비밀번호 해시 인코딩
    String encodedPassword = passwordEncoder.encode(request.password());

    // 회원 엔티티 생성
    Member member = new Member(
        request.email(),
        encodedPassword,
        request.nickname(),
        request.preferredRegion(),
        request.preferredSpecies()
    );

    // 회원 저장 및 DB UNIQUE 제약 위반 처리
    Member savedMember;
    try {
      savedMember = memberRepository.saveAndFlush(member);
    } catch (DataIntegrityViolationException e) {
      throw MemberExceptionTranslator.translateSignupException(e);
    }

    // 저장된 회원을 회원가입 응답 DTO로 변환하여 반환
    return new MemberResponseDto.SignupResponse(
        savedMember.getMemberId(),
        savedMember.getEmail(),
        savedMember.getNickname()
    );
  }

  /**
   * 탈퇴 회원을 포함하여 이메일 중복 여부를 확인한다.
   *
   * @param email 형식 및 필수값 검증을 완료한 이메일
   * @throws BusinessException 이메일이 중복된 경우
   */
  @Transactional(readOnly = true)
  public void checkEmail(String email) {
    if (memberRepository.existsByEmail(email)) {
      throw new BusinessException(MemberErrorCode.DUPLICATE_EMAIL);
    }
  }

  /**
   * 탈퇴 회원을 포함하여 닉네임 중복 여부를 확인한다.
   *
   * @param nickname 필수값 및 길이 검증을 완료한 닉네임
   * @throws BusinessException 닉네임이 중복된 경우
   */
  @Transactional(readOnly = true)
  public void checkNickname(String nickname) {
    if (memberRepository.existsByNickname(nickname)) {
      throw new BusinessException(MemberErrorCode.DUPLICATE_NICKNAME);
    }
  }

  /**
   * 회원 ID로 현재 로그인한 회원의 정보를 조회한다.
   *
   * @param memberId 인증된 현재 회원의 ID
   * @return 회원 기본 정보를 담은 {@link MemberResponseDto.MyInfoResponse}
   * @throws BusinessException 해당 ID의 회원이 존재하지 않는 경우
   */
  @Transactional(readOnly = true)
  public MemberResponseDto.MyInfoResponse getMyInfo(Long memberId) {
    // memberId로 회원 조회
    Member member = memberRepository.findById(memberId)
        .orElseThrow(
            () -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)
        );

    // 회원의 평균 평점 계산
    Double averageRating = null;

    if (member.getReviewCount() > 0) {
      averageRating = BigDecimal.valueOf(member.getRatingSum())
          .divide(
              BigDecimal.valueOf(member.getReviewCount()),
              1,
              RoundingMode.HALF_UP
          )
          .doubleValue();
    }

    // Member 정보를 Response DTO로 변환 후 반환
    return new MemberResponseDto.MyInfoResponse(
        member.getMemberId(),
        member.getEmail(),
        member.getNickname(),
        member.getPreferredRegion(),
        member.getPreferredSpecies(),
        member.getRole(),
        member.getStatus(),
        averageRating,
        member.getReviewCount()
    );
  }

  /**
   * 회원 ID로 현재 로그인한 회원의 정보를 부분 수정한다.
   *
   * @param memberId 인증된 현재 회원의 ID
   * @param request 입력 검증을 완료한 회원 정보 수정 요청
   * @return 수정된 회원 정보를 담은 {@link MemberResponseDto.UpdateMyInfoResponse}
   * @throws BusinessException 회원이 존재하지 않거나 닉네임이 중복된 경우
   * @throws DataIntegrityViolationException 닉네임 중복으로 분류할 수 없는 데이터 무결성 위반이 발생한 경우
   */
  @Transactional
  public MemberResponseDto.UpdateMyInfoResponse updateMyInfo(
      Long memberId,
      MemberRequestDto.UpdateMyInfoRequest request
  ) {
    // memberId로 회원 조회
    Member member = memberRepository.findById(memberId)
        .orElseThrow(
            () -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)
        );

    // 닉네임이 전달된 경우
    if (request.nickname().present()) {
      String newNickname = request.nickname().value();

      // 전달된 값이 현재 닉네임과 다른 경우
      if (!member.getNickname().equals(newNickname)) {

        // 닉네임 중복이면 DUPLICATE_NICKNAME 예외
        if (memberRepository.existsByNickname(newNickname)) {
          throw new BusinessException(MemberErrorCode.DUPLICATE_NICKNAME);
        }

        // 닉네임 중복이 아니면 변경
        member.changeNickname(newNickname);
      }
    }

    // 선호 지역이 전달된 경우 변경
    if (request.preferredRegion().present()) {
      member.changePreferredRegion(request.preferredRegion().value());
    }

    // 선호 동물이 전달된 경우 변경
    if (request.preferredSpecies().present()) {
      member.changePreferredSpecies(request.preferredSpecies().value());
    }

    // 변경사항을 DB에 반영하고 닉네임 UNIQUE 제약 위반 처리
    try {
      memberRepository.flush();
    } catch (DataIntegrityViolationException e) {
      throw MemberExceptionTranslator.translateUpdateMyInfoException(e);
    }

    // 변경된 회원 정보로 응답 생성 후 반환
    return new MemberResponseDto.UpdateMyInfoResponse(
        member.getMemberId(),
        member.getNickname(),
        member.getPreferredRegion(),
        member.getPreferredSpecies()
    );
  }
}
