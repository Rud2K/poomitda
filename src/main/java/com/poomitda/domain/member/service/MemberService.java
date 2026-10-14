package com.poomitda.domain.member.service;

import com.poomitda.domain.member.dto.*;
import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.exception.*;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.global.exception.exception.BusinessException;
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
   * <p>회원가입 정보를 바탕으로 회원을 생성하고 저장한다.</p>
   *
   * <p>탈퇴 회원을 포함하여 이메일과 닉네임의 중복 여부를 확인하고,
   * 비밀번호를 해시 인코딩한 후 회원 데이터를 저장한다.</p>
   *
   * <p>저장 시 식별 가능한 이메일 또는 닉네임 UNIQUE 제약 위반은
   * 해당 중복 오류로 변환하고, 그 외 데이터 무결성 예외는 그대로 전달한다.</p>
   *
   * <p>요청값의 필수 여부·형식·길이 검증은 호출 전에 완료되어야 한다.</p>
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
}
