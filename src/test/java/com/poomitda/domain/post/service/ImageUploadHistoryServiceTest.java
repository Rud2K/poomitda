package com.poomitda.domain.post.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.entity.constant.*;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.domain.post.entity.ImageUpload;
import com.poomitda.domain.post.entity.constant.ImageUploadStatus;
import com.poomitda.domain.post.repository.ImageUploadRepository;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

class ImageUploadHistoryServiceTest {

  private final MemberRepository memberRepository = mock(MemberRepository.class);

  private final ImageUploadRepository imageUploadRepository = mock(ImageUploadRepository.class);

  private final ImageUploadHistoryService service =
      new ImageUploadHistoryService(
          memberRepository,
          imageUploadRepository
      );

  private static final String IMAGE_KEY = "posts/1/example.png";

  private static final String IMAGE_URL =
      "https://poomitda-dev-images.s3.ap-northeast-2.amazonaws.com/"
          + IMAGE_KEY;

  @Test
  @DisplayName("정상 보호자의 이미지 업로드 이력을 PENDING 상태로 저장한다")
  void savesPendingHistory() {
    Member member = mock(Member.class);

    when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
    when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
    when(member.getRole()).thenReturn(MemberRole.ROLE_PROTECTOR);

    service.savePending(1L, IMAGE_KEY, IMAGE_URL);

    ArgumentCaptor<ImageUpload> captor = ArgumentCaptor.forClass(ImageUpload.class);

    verify(imageUploadRepository).save(captor.capture());

    ImageUpload saved = captor.getValue();

    assertSame(member, saved.getUploader());
    assertEquals(IMAGE_KEY, saved.getImageKey());
    assertEquals(IMAGE_URL, saved.getImageUrl());
    assertEquals(ImageUploadStatus.PENDING, saved.getStatus());
  }

  @Test
  @DisplayName("회원이 존재하지 않으면 인증 오류가 발생하고 저장하지 않는다")
  void rejectsMissingMember() {
    when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.savePending(1L, IMAGE_KEY, IMAGE_URL)
    );

    assertEquals(CommonErrorCode.UNAUTHORIZED, exception.getErrorCode());
    verifyNoInteractions(imageUploadRepository);
  }

  @Test
  @DisplayName("탈퇴한 회원이면 인증 오류가 발생하고 저장하지 않는다")
  void rejectsDeletedMember() {
    Member member = mock(Member.class);

    when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
    when(member.getStatus()).thenReturn(MemberStatus.DELETED);

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.savePending(1L, IMAGE_KEY, IMAGE_URL)
    );

    assertEquals(CommonErrorCode.UNAUTHORIZED, exception.getErrorCode());
    verifyNoInteractions(imageUploadRepository);
  }

  @Test
  @DisplayName("일반 회원이면 권한 오류가 발생하고 저장하지 않는다")
  void rejectsRegularMember() {
    Member member = mock(Member.class);

    when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
    when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
    when(member.getRole()).thenReturn(MemberRole.ROLE_USER);

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.savePending(1L, IMAGE_KEY, IMAGE_URL)
    );

    assertEquals(CommonErrorCode.FORBIDDEN, exception.getErrorCode());
    verifyNoInteractions(imageUploadRepository);
  }

  @Test
  @DisplayName("관리자도 보호자 권한이 아니면 저장할 수 없다")
  void rejectsAdmin() {
    Member member = mock(Member.class);

    when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
    when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
    when(member.getRole()).thenReturn(MemberRole.ROLE_ADMIN);

    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.savePending(1L, IMAGE_KEY, IMAGE_URL)
    );

    assertEquals(CommonErrorCode.FORBIDDEN, exception.getErrorCode());
    verifyNoInteractions(imageUploadRepository);
  }
}