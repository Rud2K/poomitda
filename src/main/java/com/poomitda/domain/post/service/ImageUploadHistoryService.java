package com.poomitda.domain.post.service;

import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.entity.constant.*;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.domain.post.entity.ImageUpload;
import com.poomitda.domain.post.repository.ImageUploadRepository;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImageUploadHistoryService {

  private final MemberRepository memberRepository;
  private final ImageUploadRepository imageUploadRepository;

  @Transactional
  public void savePending(
      Long memberId,
      String imageKey,
      String imageUrl
  ) {
    // 회원 상태 확인부터 이력 저장까지 회원 행에 잠금을 유지
    Member member = memberRepository.findByIdForUpdate(memberId)
        .orElseThrow(
            () -> new BusinessException(CommonErrorCode.UNAUTHORIZED)
        );

    if (member.getStatus() != MemberStatus.ACTIVE) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }

    if (member.getRole() != MemberRole.ROLE_PROTECTOR) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    ImageUpload imageUpload = new ImageUpload(
        member,
        imageKey,
        imageUrl
    );

    imageUploadRepository.save(imageUpload);
  }
}
