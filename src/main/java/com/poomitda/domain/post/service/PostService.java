package com.poomitda.domain.post.service;

import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.entity.constant.*;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.domain.post.dto.*;
import com.poomitda.domain.post.dto.PostRequestDto.PostImageRequest;
import com.poomitda.domain.post.entity.*;
import com.poomitda.domain.post.entity.constant.ImageUploadStatus;
import com.poomitda.domain.post.repository.*;
import com.poomitda.domain.post.validation.PostCreateValidator;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

  private final MemberRepository memberRepository;
  private final ImageUploadRepository imageUploadRepository;
  private final PostRepository postRepository;
  private final PostImageRepository postImageRepository;
  private final PostCreateValidator postCreateValidator;

  /**
   * 공고를 등록하고 검증된 업로드 이미지를 연결한다.
   *
   * @param memberId 공고를 등록할 회원 ID
   * @param request 공고 등록 요청
   * @return 등록된 공고 ID를 포함한 {@link PostResponseDto.CreatePostResponse}
   */
  @Transactional
  public PostResponseDto.CreatePostResponse createPost(
      Long memberId,
      PostRequestDto.CreatePostRequest request
  ) {
    // 모집 유형과 이미지 목록의 입력값 조합을 검증
    postCreateValidator.validate(request);

    // 작성자를 잠금 조회하고 상태와 권한을 검증
    Member author = getAuthorForUpdate(memberId);

    // 이미지 업로드 이력을 잠금 조회하고 소유자와 연결 가능 상태를 검증
    List<ImageUpload> imageUploads = getImageUploadsForUpdate(memberId, request.images());

    // 접수 마감 시각을 UTC 기준으로 변환
    LocalDateTime recruitmentDeadline = toUtcDeadline(request.recruitmentDeadline());

    // 작성자와 요청값, 변환한 마감 시각으로 공고 엔티티 생성
    Post post = new Post(
        author,
        request.title(),
        request.content(),
        request.noticeType(),
        request.species(),
        request.breed(),
        request.estimatedBirthDate(),
        request.gender(),
        request.isNeutered(),
        request.fosterPeriod(),
        request.foundLocation(),
        request.region(),
        request.district(),
        recruitmentDeadline
    );

    // 공고를 저장하고 반환된 엔티티 보관
    Post savedPost = postRepository.save(post);

    // 공고 이미지 엔티티를 담을 목록 생성
    List<PostImage> postImages = new ArrayList<>();

    // 요청한 이미지 목록을 순회
    for (PostImageRequest image : request.images()) {
      // 저장된 공고와 이미지 URL, 대표 이미지 여부, 표시 순서로 공고 이미지 엔티티를 생성하여 목록에 추가
      PostImage postImage = new PostImage(
          savedPost,
          image.imageUrl(),
          image.isRepresentative(),
          image.displayOrder()
      );

      postImages.add(postImage);
    }

    // 공고 이미지 목록 저장
    postImageRepository.saveAll(postImages);

    // 검증된 이미지 업로드 이력 목록을 순회하며 각 이력을 공고 연결 완료 상태로 변경
    for (ImageUpload imageUpload : imageUploads) {
      imageUpload.attachToPost();
    }

    // 저장된 공고 ID를 포함한 응답 생성 후 반환
    return new PostResponseDto.CreatePostResponse(savedPost.getPostId());
  }

  /**
   * 회원을 잠금 조회하고 공고 등록이 가능한 상태와 권한인지 확인한다.
   *
   * @param memberId 공고를 등록할 회원 ID
   * @return 공고 등록이 가능한 {@link Member}
   */
  private Member getAuthorForUpdate(Long memberId) {
    // 회원을 잠금 조회하고, 존재하지 않는 경우 UNAUTHORIZED 예외 발생
    Member member = memberRepository.findByIdForUpdate(memberId)
        .orElseThrow(
            () -> new BusinessException(CommonErrorCode.UNAUTHORIZED)
        );

    // 회원 상태가 ACTIVE가 아닌 경우 UNAUTHORIZED 예외 발생
    if (member.getStatus() != MemberStatus.ACTIVE) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }

    // 회원 역할이 ROLE_PROTECTOR가 아닌 경우 FORBIDDEN 예외 발생
    if (member.getRole() != MemberRole.ROLE_PROTECTOR) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    // 검증을 모두 통과한 회원을 반환
    return member;
  }

  /**
   * 이미지 업로드 이력을 잠금 조회하고 소유자와 연결 가능 상태를 확인한다.
   *
   * @param memberId 공고를 등록할 회원 ID
   * @param images 공고에 연결할 이미지 요청 목록
   * @return 검증을 통과한 {@link ImageUpload}의 {@link List}
   */
  private List<ImageUpload> getImageUploadsForUpdate(
      Long memberId,
      List<PostImageRequest> images
  ) {
    // 이미지 요청 목록에서 imageUrl을 추출하여 List<String> 생성
    List<String> imageUrls = new ArrayList<>();

    for (PostImageRequest image : images) {
      imageUrls.add(image.imageUrl());
    }

    // 이력 잠금 조회
    List<ImageUpload> imageUploads = imageUploadRepository.findAllByImageUrlInForUpdate(imageUrls);

    // 조회된 이력 수와 요청한 URL 수가 다른지 확인
    if (imageUploads.size() != imageUrls.size()) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    // 조회된 이력 목록 순회
    for (ImageUpload imageUpload : imageUploads) {
      // 업로더의 회원 ID가 memberId와 다른 경우
      if (!imageUpload.getUploader().getMemberId().equals(memberId)) {
        throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
      }

      // 업로드 상태가 PENDING이 아닌 경우
      if (imageUpload.getStatus() != ImageUploadStatus.PENDING) {
        throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
      }
    }

    // 검증을 통과한 업로드 이력 목록 반환
    return imageUploads;
  }

  /**
   * 접수 마감 시각을 UTC 기준의 DB 저장용 시각으로 변환한다.
   *
   * @param recruitmentDeadline 요청한 접수 마감 시각
   * @return UTC 기준 접수 마감 시각인 {@link LocalDateTime}, 입력이 null인 경우 null
   */
  private LocalDateTime toUtcDeadline(OffsetDateTime recruitmentDeadline) {
    // 접수 마감 시각이 null인 경우, null 반환
    if (recruitmentDeadline == null) return null;

    // 동일한 순간의 UTC 시각으로 변환한 뒤 LocalDateTime으로 반환
    return recruitmentDeadline.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
  }
}
