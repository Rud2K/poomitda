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
import com.poomitda.global.request.PatchField;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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

  @Test
  @DisplayName("닉네임만 수정 -> 닉네임 변경 및 선호 정보 유지")
  void updateMyInfoNicknameOnlyTest() {
    // given
    Long memberId = 1L;
    Member member = new Member(
        "member@example.com",
        "encoded-password",
        "기존닉네임",
        Region.SEOUL,
        Species.DOG
    );
    ReflectionTestUtils.setField(member, "memberId", memberId);

    MemberRequestDto.UpdateMyInfoRequest request =
        new MemberRequestDto.UpdateMyInfoRequest(
            PatchField.supplied("새닉네임"),
            PatchField.absent(),
            PatchField.absent()
        );

    when(memberRepository.findById(memberId))
        .thenReturn(Optional.of(member));
    when(memberRepository.existsByNickname("새닉네임"))
        .thenReturn(false);

    // when
    MemberResponseDto.UpdateMyInfoResponse response =
        memberService.updateMyInfo(memberId, request);

    // then
    assertAll(
        () -> assertEquals("새닉네임", member.getNickname()),
        () -> assertEquals(Region.SEOUL, member.getPreferredRegion()),
        () -> assertEquals(Species.DOG, member.getPreferredSpecies()),
        () -> assertEquals(memberId, response.memberId()),
        () -> assertEquals("새닉네임", response.nickname()),
        () -> assertEquals(Region.SEOUL, response.preferredRegion()),
        () -> assertEquals(Species.DOG, response.preferredSpecies())
    );

    verify(memberRepository).existsByNickname("새닉네임");
    verify(memberRepository).flush();
  }

  @Test
  @DisplayName("선호 정보에 null 전달 -> 설정 해제 및 닉네임 유지")
  void updateMyInfoClearPreferencesTest() {
    // given
    Long memberId = 1L;
    Member member = new Member(
        "member@example.com",
        "encoded-password",
        "기존닉네임",
        Region.SEOUL,
        Species.DOG
    );
    ReflectionTestUtils.setField(member, "memberId", memberId);

    MemberRequestDto.UpdateMyInfoRequest request =
        new MemberRequestDto.UpdateMyInfoRequest(
            PatchField.absent(),
            PatchField.supplied(null),
            PatchField.supplied(null)
        );

    when(memberRepository.findById(memberId))
        .thenReturn(Optional.of(member));

    // when
    MemberResponseDto.UpdateMyInfoResponse response =
        memberService.updateMyInfo(memberId, request);

    // then
    assertAll(
        () -> assertEquals("기존닉네임", member.getNickname()),
        () -> assertNull(member.getPreferredRegion()),
        () -> assertNull(member.getPreferredSpecies()),
        () -> assertEquals(memberId, response.memberId()),
        () -> assertEquals("기존닉네임", response.nickname()),
        () -> assertNull(response.preferredRegion()),
        () -> assertNull(response.preferredSpecies())
    );

    verify(memberRepository, never()).existsByNickname(anyString());
    verify(memberRepository).flush();
  }

  @Test
  @DisplayName("현재 닉네임과 같은 값 전달 -> 중복 검사 생략 및 다른 필드 수정")
  void updateMyInfoSameNicknameTest() {
    // given
    Long memberId = 1L;
    Member member = new Member(
        "member@example.com",
        "encoded-password",
        "기존닉네임",
        Region.SEOUL,
        Species.DOG
    );
    ReflectionTestUtils.setField(member, "memberId", memberId);

    MemberRequestDto.UpdateMyInfoRequest request =
        new MemberRequestDto.UpdateMyInfoRequest(
            PatchField.supplied("기존닉네임"),
            PatchField.supplied(Region.GYEONGGI),
            PatchField.absent()
        );

    when(memberRepository.findById(memberId))
        .thenReturn(Optional.of(member));

    // when
    MemberResponseDto.UpdateMyInfoResponse response =
        memberService.updateMyInfo(memberId, request);

    // then
    assertAll(
        () -> assertEquals("기존닉네임", member.getNickname()),
        () -> assertEquals(Region.GYEONGGI, member.getPreferredRegion()),
        () -> assertEquals(Species.DOG, member.getPreferredSpecies()),
        () -> assertEquals(memberId, response.memberId()),
        () -> assertEquals("기존닉네임", response.nickname()),
        () -> assertEquals(Region.GYEONGGI, response.preferredRegion()),
        () -> assertEquals(Species.DOG, response.preferredSpecies())
    );

    verify(memberRepository, never()).existsByNickname(anyString());
    verify(memberRepository).flush();
  }

  @Test
  @DisplayName("중복 닉네임 전달 -> 중복 오류 및 변경 중단")
  void updateMyInfoDuplicateNicknameTest() {
    // given
    Long memberId = 1L;
    Member member = new Member(
        "member@example.com",
        "encoded-password",
        "기존닉네임",
        Region.SEOUL,
        Species.DOG
    );
    ReflectionTestUtils.setField(member, "memberId", memberId);

    MemberRequestDto.UpdateMyInfoRequest request =
        new MemberRequestDto.UpdateMyInfoRequest(
            PatchField.supplied("사용중인닉네임"),
            PatchField.supplied(Region.GYEONGGI),
            PatchField.supplied(Species.CAT)
        );

    when(memberRepository.findById(memberId))
        .thenReturn(Optional.of(member));
    when(memberRepository.existsByNickname("사용중인닉네임"))
        .thenReturn(true);

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> memberService.updateMyInfo(memberId, request)
    );

    // then
    assertAll(
        () -> assertEquals(
            MemberErrorCode.DUPLICATE_NICKNAME,
            exception.getErrorCode()
        ),
        () -> assertEquals("기존닉네임", member.getNickname()),
        () -> assertEquals(Region.SEOUL, member.getPreferredRegion()),
        () -> assertEquals(Species.DOG, member.getPreferredSpecies())
    );

    verify(memberRepository).existsByNickname("사용중인닉네임");
    verify(memberRepository, never()).flush();
  }

  @Test
  @DisplayName("수정 대상 회원 없음 -> 회원 없음 오류")
  void updateMyInfoMemberNotFoundTest() {
    // given
    Long memberId = 1L;

    MemberRequestDto.UpdateMyInfoRequest request =
        new MemberRequestDto.UpdateMyInfoRequest(
            PatchField.supplied("새닉네임"),
            PatchField.absent(),
            PatchField.absent()
        );

    when(memberRepository.findById(memberId))
        .thenReturn(Optional.empty());

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> memberService.updateMyInfo(memberId, request)
    );

    // then
    assertEquals(
        MemberErrorCode.MEMBER_NOT_FOUND,
        exception.getErrorCode()
    );

    verify(memberRepository).findById(memberId);
    verifyNoMoreInteractions(memberRepository);
  }

  @Test
  @DisplayName("변경사항 반영 중 닉네임 중복 -> 닉네임 중복 오류로 변환")
  void updateMyInfoNicknameConstraintViolationTest() {
    // given
    Long memberId = 1L;
    Member member = new Member(
        "member@example.com",
        "encoded-password",
        "기존닉네임",
        Region.SEOUL,
        Species.DOG
    );

    MemberRequestDto.UpdateMyInfoRequest request =
        new MemberRequestDto.UpdateMyInfoRequest(
            PatchField.supplied("새닉네임"),
            PatchField.absent(),
            PatchField.absent()
        );

    ConstraintViolationException cause =
        new ConstraintViolationException(
            "테스트용 닉네임 UNIQUE 제약 위반",
            new SQLException("테스트용 중복 변경 오류"),
            "members.uk_members_nickname"
        );

    DataIntegrityViolationException flushException =
        new DataIntegrityViolationException("회원 정보 변경 실패", cause);

    when(memberRepository.findById(memberId))
        .thenReturn(Optional.of(member));
    when(memberRepository.existsByNickname("새닉네임"))
        .thenReturn(false);

    doThrow(flushException).when(memberRepository).flush();

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> memberService.updateMyInfo(memberId, request)
    );

    // then
    assertEquals(
        MemberErrorCode.DUPLICATE_NICKNAME,
        exception.getErrorCode()
    );

    verify(memberRepository).existsByNickname("새닉네임");
    verify(memberRepository).flush();
  }

  @Test
  @DisplayName("변경사항 반영 중 알 수 없는 제약 위반 -> 기존 예외 전달")
  void updateMyInfoUnknownConstraintViolationTest() {
    // given
    Long memberId = 1L;
    Member member = new Member(
        "member@example.com",
        "encoded-password",
        "기존닉네임",
        Region.SEOUL,
        Species.DOG
    );

    MemberRequestDto.UpdateMyInfoRequest request =
        new MemberRequestDto.UpdateMyInfoRequest(
            PatchField.absent(),
            PatchField.supplied(Region.GYEONGGI),
            PatchField.absent()
        );

    ConstraintViolationException cause =
        new ConstraintViolationException(
            "테스트용 미분류 제약 위반",
            new SQLException("테스트용 변경 오류"),
            "members.unknown_constraint"
        );

    DataIntegrityViolationException flushException =
        new DataIntegrityViolationException("회원 정보 변경 실패", cause);

    when(memberRepository.findById(memberId))
        .thenReturn(Optional.of(member));

    doThrow(flushException).when(memberRepository).flush();

    // when
    DataIntegrityViolationException exception = assertThrows(
        DataIntegrityViolationException.class,
        () -> memberService.updateMyInfo(memberId, request)
    );

    // then
    assertSame(flushException, exception);

    verify(memberRepository, never()).existsByNickname(anyString());
    verify(memberRepository).flush();
  }
}