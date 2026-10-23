package com.poomitda.domain.member.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.poomitda.domain.member.dto.*;
import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.entity.constant.*;
import com.poomitda.domain.member.exception.MemberErrorCode;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.global.constant.*;
import com.poomitda.global.exception.exception.BusinessException;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

  @Mock
  private MemberRepository memberRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @InjectMocks
  private MemberService memberService;

  @Test
  @DisplayName("이메일 중복 -> 회원가입 중단")
  void signupDuplicateEmailTest() {
    // given
    MemberRequestDto.SignupRequest request = new MemberRequestDto.SignupRequest(
        "member@example.com",
        "Password1234!",
        "품잇다",
        null,
        null
    );

    when(memberRepository.existsByEmail(request.email())).thenReturn(true);

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> memberService.signup(request)
    );

    // then
    assertEquals(
        MemberErrorCode.DUPLICATE_EMAIL,
        exception.getErrorCode()
    );

    verify(memberRepository).existsByEmail(request.email());
    verifyNoMoreInteractions(memberRepository);
    verifyNoInteractions(passwordEncoder);
  }

  @Test
  @DisplayName("닉네임 중복 -> 회원가입 중단")
  void signupDuplicateNicknameTest() {
    // given
    MemberRequestDto.SignupRequest request = new MemberRequestDto.SignupRequest(
        "member@example.com",
        "Password1234!",
        "품잇다",
        null,
        null
    );

    when(memberRepository.existsByEmail(request.email())).thenReturn(false);
    when(memberRepository.existsByNickname(request.nickname())).thenReturn(true);

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> memberService.signup(request)
    );

    // then
    assertEquals(
        MemberErrorCode.DUPLICATE_NICKNAME,
        exception.getErrorCode()
    );

    verify(memberRepository).existsByEmail(request.email());
    verify(memberRepository).existsByNickname(request.nickname());
    verifyNoMoreInteractions(memberRepository);
    verifyNoInteractions(passwordEncoder);
  }

  @Test
  @DisplayName("저장 중 이메일 중복 -> 이메일 중복 오류로 변환")
  void signupEmailConstraintViolationTest() {
    // given
    MemberRequestDto.SignupRequest request = new MemberRequestDto.SignupRequest(
        "member@example.com",
        "Password1234!",
        "품잇다",
        null,
        null
    );

    ConstraintViolationException cause = new ConstraintViolationException(
        "테스트용 이메일 UNIQUE 제약 위반",
        new SQLException("테스트용 중복 저장 오류"),
        "members.uk_members_email"
    );

    DataIntegrityViolationException saveException = new DataIntegrityViolationException(
        "회원 저장 실패",
        cause
    );

    when(memberRepository.existsByEmail(request.email())).thenReturn(false);
    when(memberRepository.existsByNickname(request.nickname())).thenReturn(false);
    when(passwordEncoder.encode(request.password())).thenReturn("encodedPassword");
    when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(saveException);

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> memberService.signup(request)
    );

    // then
    assertEquals(
        MemberErrorCode.DUPLICATE_EMAIL,
        exception.getErrorCode()
    );

    verify(memberRepository).existsByEmail(request.email());
    verify(memberRepository).existsByNickname(request.nickname());
    verify(memberRepository).saveAndFlush(any(Member.class));
    verifyNoMoreInteractions(memberRepository);
  }

  @Test
  @DisplayName("저장 중 닉네임 중복 -> 닉네임 중복 오류로 변환")
  void signupNicknameConstraintViolationTest() {
    // given
    MemberRequestDto.SignupRequest request = new MemberRequestDto.SignupRequest(
        "member@example.com",
        "Password1234!",
        "품잇다",
        null,
        null
    );

    ConstraintViolationException cause = new ConstraintViolationException(
        "테스트용 닉네임 UNIQUE 제약 위반",
        new SQLException("테스트용 중복 저장 오류"),
        "members.uk_members_nickname"
    );

    DataIntegrityViolationException saveException = new DataIntegrityViolationException(
        "회원 저장 실패",
        cause
    );

    when(memberRepository.existsByEmail(request.email())).thenReturn(false);
    when(memberRepository.existsByNickname(request.nickname())).thenReturn(false);
    when(passwordEncoder.encode(request.password())).thenReturn("encodedPassword");
    when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(saveException);

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> memberService.signup(request)
    );

    // then
    assertEquals(
        MemberErrorCode.DUPLICATE_NICKNAME,
        exception.getErrorCode()
    );

    verify(memberRepository).existsByEmail(request.email());
    verify(memberRepository).existsByNickname(request.nickname());
    verify(memberRepository).saveAndFlush(any(Member.class));
    verifyNoMoreInteractions(memberRepository);
  }

  @Test
  @DisplayName("알 수 없는 제약 위반 -> 기존 예외 전달")
  void signupUnknownConstraintViolationTest() {
    // given
    MemberRequestDto.SignupRequest request = new MemberRequestDto.SignupRequest(
        "member@example.com",
        "Password1234!",
        "품잇다",
        null,
        null
    );

    ConstraintViolationException cause = new ConstraintViolationException(
        "테스트용 미분류 제약 위반",
        new SQLException("테스트용 저장 오류"),
        "members.unknown_constraint"
    );

    DataIntegrityViolationException saveException = new DataIntegrityViolationException(
        "회원 저장 실패",
        cause
    );

    when(memberRepository.existsByEmail(request.email())).thenReturn(false);
    when(memberRepository.existsByNickname(request.nickname())).thenReturn(false);
    when(passwordEncoder.encode(request.password())).thenReturn("encodedPassword");
    when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(saveException);

    // when
    DataIntegrityViolationException exception = assertThrows(
        DataIntegrityViolationException.class,
        () -> memberService.signup(request)
    );

    // then
    assertSame(saveException, exception);

    verify(memberRepository).existsByEmail(request.email());
    verify(memberRepository).existsByNickname(request.nickname());
    verify(memberRepository).saveAndFlush(any(Member.class));
    verifyNoMoreInteractions(memberRepository);
  }

  @Test
  @DisplayName("정상 회원가입 -> 비밀번호 인코딩 및 회원 정보 반환")
  void signupSuccessTest() {
    // given
    MemberRequestDto.SignupRequest request = new MemberRequestDto.SignupRequest(
        "member@example.com",
        "Password1234!",
        "품잇다",
        Region.BUSAN,
        Species.CAT
    );

    String encodedPassword = "encodedPassword";

    Member savedMember = mock(Member.class);
    when(savedMember.getMemberId()).thenReturn(1L);
    when(savedMember.getEmail()).thenReturn(request.email());
    when(savedMember.getNickname()).thenReturn(request.nickname());

    when(memberRepository.existsByEmail(request.email())).thenReturn(false);
    when(memberRepository.existsByNickname(request.nickname())).thenReturn(false);
    when(passwordEncoder.encode(request.password())).thenReturn(encodedPassword);
    when(memberRepository.saveAndFlush(any(Member.class))).thenReturn(savedMember);

    // when
    MemberResponseDto.SignupResponse response = memberService.signup(request);

    // then
    ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
    verify(memberRepository).saveAndFlush(memberCaptor.capture());

    Member memberToSave = memberCaptor.getValue();

    assertAll(
        () -> assertEquals(request.email(), memberToSave.getEmail()),
        () -> assertEquals(request.nickname(), memberToSave.getNickname()),
        () -> assertEquals(encodedPassword, memberToSave.getPassword()),
        () -> assertEquals(request.preferredRegion(), memberToSave.getPreferredRegion()),
        () -> assertEquals(request.preferredSpecies(), memberToSave.getPreferredSpecies()),
        () -> assertEquals(MemberRole.ROLE_USER, memberToSave.getRole()),
        () -> assertEquals(MemberStatus.ACTIVE, memberToSave.getStatus()),
        () -> assertEquals(0L, memberToSave.getRatingSum()),
        () -> assertEquals(0, memberToSave.getReviewCount()),
        () -> assertNull(memberToSave.getDeletedAt())
    );

    assertAll(
        () -> assertEquals(Long.valueOf(1L), response.memberId()),
        () -> assertEquals(request.email(), response.email()),
        () -> assertEquals(request.nickname(), response.nickname())
    );

    verify(passwordEncoder).encode(request.password());
  }

  @Test
  @DisplayName("이메일 사용 가능 -> 성공")
  void checkEmailAvailableTest() {
    // given
    String email = "available@example";
    when(memberRepository.existsByEmail(email)).thenReturn(false);

    // when & then
    assertDoesNotThrow(() -> memberService.checkEmail(email));

    verify(memberRepository).existsByEmail(email);
    verifyNoMoreInteractions(memberRepository);
    verifyNoInteractions(passwordEncoder);
  }

  @Test
  @DisplayName("이메일 중복 -> 이메일 중복 오류")
  void checkEmailDuplicateTest() {
    // given
    String email = "duplicate@example.com";
    when(memberRepository.existsByEmail(email)).thenReturn(true);

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> memberService.checkEmail(email)
    );

    // then
    assertEquals(
        MemberErrorCode.DUPLICATE_EMAIL,
        exception.getErrorCode()
    );

    verify(memberRepository).existsByEmail(email);
    verifyNoMoreInteractions(memberRepository);
    verifyNoInteractions(passwordEncoder);
  }

  @Test
  @DisplayName("닉네임 사용 가능 -> 성공")
  void checkNicknameAvailableTest() {
    // given
    String nickname = "available_nickname";
    when(memberRepository.existsByNickname(nickname)).thenReturn(false);

    // when & then
    assertDoesNotThrow(() -> memberService.checkNickname(nickname));

    verify(memberRepository).existsByNickname(nickname);
    verifyNoMoreInteractions(memberRepository);
    verifyNoInteractions(passwordEncoder);
  }

  @Test
  @DisplayName("닉네임 중복 -> 닉네임 중복 오류")
  void checkNicknameDuplicateTest() {
    // given
    String nickname = "duplicate_nickname";
    when(memberRepository.existsByNickname(nickname)).thenReturn(true);

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> memberService.checkNickname(nickname)
    );

    // then
    assertEquals(
        MemberErrorCode.DUPLICATE_NICKNAME,
        exception.getErrorCode()
    );

    verify(memberRepository).existsByNickname(nickname);
    verifyNoMoreInteractions(memberRepository);
    verifyNoInteractions(passwordEncoder);
  }

  @Test
  @DisplayName("후기가 없는 회원 조회 -> 기본 정보와 null 평균 반환")
  void getMyInfoWithoutReviewsTest() {
    // given
    Long memberId = 1L;

    Member member = mock(Member.class);
    when(member.getMemberId()).thenReturn(memberId);
    when(member.getEmail()).thenReturn("member@example.com");
    when(member.getNickname()).thenReturn("품잇다");
    when(member.getPreferredRegion()).thenReturn(Region.BUSAN);
    when(member.getPreferredSpecies()).thenReturn(Species.CAT);
    when(member.getRole()).thenReturn(MemberRole.ROLE_USER);
    when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
    when(member.getReviewCount()).thenReturn(0);

    when(memberRepository.findById(memberId))
        .thenReturn(Optional.of(member));

    // when
    MemberResponseDto.MyInfoResponse response = memberService.getMyInfo(memberId);

    // then
    assertAll(
        () -> assertEquals(memberId, response.memberId()),
        () -> assertEquals("member@example.com", response.email()),
        () -> assertEquals("품잇다", response.nickname()),
        () -> assertEquals(Region.BUSAN, response.preferredRegion()),
        () -> assertEquals(Species.CAT, response.preferredSpecies()),
        () -> assertEquals(MemberRole.ROLE_USER, response.role()),
        () -> assertEquals(MemberStatus.ACTIVE, response.status()),
        () -> assertNull(response.averageRating()),
        () -> assertEquals(0, response.reviewCount())
    );

    verify(memberRepository).findById(memberId);
  }

  @Test
  @DisplayName("평균 평점이 4.25인 회원 조회 -> 소수 첫째 자리 HALF_UP 반올림")
  void getMyInfoAverageRatingHalfUpTest() {
    // given
    Long memberId = 1L;

    Member member = mock(Member.class);
    when(member.getRatingSum()).thenReturn(17L);
    when(member.getReviewCount()).thenReturn(4);

    when(memberRepository.findById(memberId))
        .thenReturn(Optional.of(member));

    // when
    MemberResponseDto.MyInfoResponse response = memberService.getMyInfo(memberId);

    // then
    assertAll(
        () -> assertEquals(Double.valueOf(4.3), response.averageRating()),
        () -> assertEquals(4, response.reviewCount())
    );

    verify(memberRepository).findById(memberId);
  }

  @Test
  @DisplayName("존재하지 않는 회원 조회 -> 회원 없음 오류")
  void getMyInfoMemberNotFoundTest() {
    // given
    Long memberId = 1L;

    when(memberRepository.findById(memberId))
        .thenReturn(Optional.empty());

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> memberService.getMyInfo(memberId)
    );

    // then
    assertEquals(
        MemberErrorCode.MEMBER_NOT_FOUND,
        exception.getErrorCode()
    );

    verify(memberRepository).findById(memberId);
  }
}