package com.poomitda.domain.member.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.entity.constant.MemberRole;
import com.poomitda.domain.member.entity.constant.MemberStatus;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.global.security.principal.MemberPrincipal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.BadCredentialsException;

@ExtendWith(MockitoExtension.class)
class MemberAuthenticationServiceTest {

  @Mock
  private MemberRepository memberRepository;

  @InjectMocks
  private MemberAuthenticationService memberAuthenticationService;

  @Test
  @DisplayName("ACTIVE 회원 -> DB 회원 ID와 현재 권한 반환")
  void authenticateActiveMemberTest() {
    // given
    Member member = mock(Member.class);

    when(member.getMemberId()).thenReturn(7L);
    when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
    when(member.getRole()).thenReturn(MemberRole.ROLE_PROTECTOR);
    when(memberRepository.findById(7L)).thenReturn(Optional.of(member));

    // when
    MemberPrincipal principal = memberAuthenticationService.authenticate(7L);

    // then
    assertAll(
        () -> assertEquals(Long.valueOf(7L), principal.memberId()),
        () -> assertEquals(MemberRole.ROLE_PROTECTOR, principal.role())
    );
    verify(memberRepository).findById(7L);
  }

  @Test
  @DisplayName("DB 권한 변경 -> 다음 인증에 변경된 권한 반영")
  void authenticateUsesLatestRoleTest() {
    // given
    Member member = mock(Member.class);

    when(member.getMemberId()).thenReturn(1L);
    when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
    when(member.getRole()).thenReturn(MemberRole.ROLE_ADMIN, MemberRole.ROLE_USER);
    when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

    // when
    MemberPrincipal before = memberAuthenticationService.authenticate(1L);
    MemberPrincipal after = memberAuthenticationService.authenticate(1L);

    // then
    assertEquals(MemberRole.ROLE_ADMIN, before.role());
    assertEquals(MemberRole.ROLE_USER, after.role());
    verify(memberRepository, times(2)).findById(1L);
  }

  @Test
  @DisplayName("존재하지 않는 회원 -> 인증 거절")
  void authenticateMemberNotFoundTest() {
    when(memberRepository.findById(1L)).thenReturn(Optional.empty());

    assertThrows(
        BadCredentialsException.class,
        () -> memberAuthenticationService.authenticate(1L)
    );
  }

  @Test
  @DisplayName("탈퇴 회원 -> 인증 거절")
  void authenticateDeletedMemberTest() {
    Member member = mock(Member.class);

    when(member.getStatus()).thenReturn(MemberStatus.DELETED);
    when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

    assertThrows(
        BadCredentialsException.class,
        () -> memberAuthenticationService.authenticate(1L)
    );
  }

  @Test
  @DisplayName("DB 장애 -> 인증 실패로 변환하지 않고 원래 예외 전달")
  void authenticateDatabaseFailureTest() {
    DataAccessResourceFailureException failure = new DataAccessResourceFailureException(
        "테스트용 DB 연결 실패"
    );

    when(memberRepository.findById(1L)).thenThrow(failure);

    DataAccessResourceFailureException actual = assertThrows(
        DataAccessResourceFailureException.class,
        () -> memberAuthenticationService.authenticate(1L)
    );

    assertSame(failure, actual);
  }
}
