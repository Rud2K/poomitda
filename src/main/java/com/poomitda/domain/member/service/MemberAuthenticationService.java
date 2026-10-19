package com.poomitda.domain.member.service;

import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.entity.constant.MemberStatus;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.global.security.principal.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberAuthenticationService {

  private final MemberRepository memberRepository;

  @Transactional(readOnly = true)
  public MemberPrincipal authenticate(Long memberId) {
    // memberId로 회원 조회
    Member member = memberRepository.findById(memberId).orElseThrow(
        () -> new BadCredentialsException("인증할 수 없는 회원입니다.")
    );

    // 회원 상태가 ACTIVE가 아니면 인증 예외 발생
    if (member.getStatus() != MemberStatus.ACTIVE) {
      throw new BadCredentialsException("인증할 수 없는 회원입니다.");
    }

    // DB의 회원 ID와 권한으로 MemberPrincipal 생성 및 반환
    return new MemberPrincipal(
        member.getMemberId(),
        member.getRole()
    );
  }
}
