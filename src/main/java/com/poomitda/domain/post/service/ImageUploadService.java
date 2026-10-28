package com.poomitda.domain.post.service;

import com.poomitda.domain.member.entity.constant.MemberRole;
import com.poomitda.domain.member.service.MemberAuthenticationService;
import com.poomitda.domain.post.dto.PostResponseDto;
import com.poomitda.domain.post.validation.*;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import com.poomitda.global.security.principal.MemberPrincipal;
import com.poomitda.global.storage.S3ImageStorage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUploadService {

  private final MemberAuthenticationService memberAuthenticationService;
  private final ImageFileValidator imageFileValidator;
  private final S3ImageStorage imageStorage;
  private final ImageUploadHistoryService imageUploadHistoryService;

  public PostResponseDto.ImageUploadResponse upload(
      Long memberId,
      MultipartFile file
  ) {
    // 업로드 전, 현재 회원 상태와 권한 확인
    MemberPrincipal member = memberAuthenticationService.authenticate(memberId);

    if (member.role() != MemberRole.ROLE_PROTECTOR) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    // 실제 파일 내용 검증
    ValidatedImage image = imageFileValidator.validate(file);

    // 원본 파일명 대신 UUID와 검증된 확장자로 저장 경로 생성
    String imageKey = "posts/%d/%s.%s".formatted(
        member.memberId(),
        UUID.randomUUID(),
        image.format().getExtension()
    );

    String imageUrl = imageStorage.getImageUrl(imageKey);

    // 검증된 파일과 Content-Type으로 S3에 업로드
    imageStorage.upload(
        imageKey,
        image.bytes(),
        image.format().getContentType()
    );

    // DB 저장 또는 커밋에 실패하면 업로드한 이미지를 정리
    try {
      imageUploadHistoryService.savePending(
          member.memberId(),
          imageKey,
          imageUrl
      );
    } catch (RuntimeException exception) {
      deleteUploadedImage(imageKey, exception);
      throw exception;
    }

    return new PostResponseDto.ImageUploadResponse(
        imageKey,
        imageUrl
    );
  }

  private void deleteUploadedImage(
      String imageKey,
      RuntimeException originalException
  ) {
    try {
      imageStorage.delete(imageKey);
    } catch (RuntimeException cleanupException) {
      originalException.addSuppressed(cleanupException);
      log.error(
          "업로드 이력 저장 실패 후 S3 이미지 삭제에 실패했습니다. imageKey={}",
          imageKey,
          cleanupException
      );
    }
  }
}
