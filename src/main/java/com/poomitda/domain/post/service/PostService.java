package com.poomitda.domain.post.service;

import com.poomitda.domain.matching.repository.ApplicationRepository;
import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.entity.constant.*;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.domain.post.cursor.*;
import com.poomitda.domain.post.dto.*;
import com.poomitda.domain.post.dto.PostRequestDto.*;
import com.poomitda.domain.post.dto.PostResponseDto.*;
import com.poomitda.domain.post.entity.*;
import com.poomitda.domain.post.entity.constant.*;
import com.poomitda.domain.post.exception.PostErrorCode;
import com.poomitda.domain.post.repository.*;
import com.poomitda.domain.post.repository.PostImageRepository.RepresentativeImageView;
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
  private final BookmarkRepository bookmarkRepository;
  private final ApplicationRepository applicationRepository;
  private final PostCursorFactory postCursorFactory;
  private final PostCursorCodec postCursorCodec;

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
   * 삭제되지 않은 공고의 상세 정보와 현재 회원의 이용 가능 정보를 조회한다.
   *
   * @param memberId 현재 로그인한 회원 ID
   * @param postId 조회할 공고 ID
   * @return 공고 상세 정보를 담은 {@link PostResponseDto.PostDetailResponse}
   */
  @Transactional(readOnly = true)
  public PostResponseDto.PostDetailResponse getPostDetail(
      Long memberId,
      Long postId
  ) {
    // 삭제되지 않은 공고를 작성자 정보와 함께 조회
    Post post = postRepository.findByPostIdAndDeletedAtIsNull(postId)
        .orElseThrow(
            () -> new BusinessException(PostErrorCode.POST_NOT_FOUND)
        );

    // 조회된 공고에서 작성자를 가져옴
    Member author = post.getAuthor();

    // 작성자의 회원 ID, 닉네임, 평균 평점, 후기 수를 작성자 응답 DTO에 담음
    PostResponseDto.PostWriterResponse writerResponse = new PostWriterResponse(
        author.getMemberId(),
        author.getNickname(),
        author.calculateAverageRating(),
        author.getReviewCount()
    );

    // 현재 로그인 회원의 ID와 공고 작성자의 ID를 비교하여 본인 작성 여부 결정
    boolean isWriter = memberId.equals(author.getMemberId());

    // 현재 로그인 회원의 해당 공고 북마크 여부 확인
    boolean isBookmarked = bookmarkRepository.existsByMember_MemberIdAndPost_PostId(memberId, postId);

    // 공고 이미지를 표시 순서 오름차순으로 조회
    List<PostImage> postImages = postImageRepository.findAllByPost_PostIdOrderByDisplayOrderAsc(postId);

    // 순서를 유지하며 각 이미지를 이미지 응답 DTO로 변환
    List<PostResponseDto.PostImageResponse> imageResponses = new ArrayList<>();
    for (PostImage postImage : postImages) {
      PostResponseDto.PostImageResponse imageResponse = new PostResponseDto.PostImageResponse(
          postImage.getPostImageId(),
          postImage.getImageUrl(),
          postImage.isRepresentative(),
          postImage.getDisplayOrder()
      );
      imageResponses.add(imageResponse);
    }

    // 현재 로그인 회원의 해당 공고 신청 이력을 상태와 관계없이 확인
    boolean hasApplied = applicationRepository.existsByPost_PostIdAndApplicant_MemberId(postId, memberId);

    // 신청 가능 여부를 판단할 현재 UTC 시각을 한 번 구함
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

    // 마감 시각이 없거나 현재 시각이 마감 시각보다 이전인지 확인
    LocalDateTime recruitmentDeadline = post.getRecruitmentDeadline();
    boolean isBeforeDeadline = recruitmentDeadline == null || now.isBefore(recruitmentDeadline);

    // 다음 조건을 모두 만족하면 신청 가능으로 판단
    // - 본인이 작성한 공고가 아님
    // - 기존 신청 이력이 없음
    // - 접수 마감 시각이 없거나 현재 시각이 마감 시각보다 이전임
    // - 공고 상태가 RECRUITING
    boolean canApply = !isWriter && !hasApplied && isBeforeDeadline && post.getStatus() == PostStatus.RECRUITING;

    // 생성, 수정, 마감, 종료 시각을 UTC 기준에서 +09:00 응답 시각으로 변환
    OffsetDateTime createdAt = toResponseDateTime(post.getCreatedAt());
    OffsetDateTime updatedAt = toResponseDateTime(post.getUpdatedAt());

    // 변환 직후 동일한 순간을 유지하고, null은 그대로 유지
    OffsetDateTime recruitmentDeadlineResponse = toResponseDateTime(recruitmentDeadline);
    OffsetDateTime closedAt = toResponseDateTime(post.getClosedAt());

    // 모든 정보를 상세 응답 DTO에 담아 반환
    return new PostResponseDto.PostDetailResponse(
        post.getPostId(),
        isWriter,
        isBookmarked,
        writerResponse,
        post.getTitle(),
        post.getContent(),
        post.getNoticeType(),
        post.getSpecies(),
        post.getBreed(),
        post.getEstimatedBirthDate(),
        post.getGender(),
        post.isNeutered(),
        post.getFosterPeriod(),
        post.getFoundLocation(),
        post.getRegion(),
        post.getDistrict(),
        post.getStatus(),
        post.getClosedReason(),
        recruitmentDeadlineResponse,
        post.getBookmarkCount(),
        imageResponses,
        createdAt,
        updatedAt,
        closedAt,
        canApply
    );
  }

  /**
   * 마감 상태 갱신 이후 공고 목록과 다음 페이지 정보를 조회한다.
   *
   * @param request 검증된 목록 검색 요청
   * @param cursor 검증된 커서. 첫 페이지에서는 null
   * @param referenceDate 첫 페이지부터 유지하는 나이 계산 기준일
   * @param now 현재 요청의 마감 여부를 판단할 UTC 기준 시각
   * @return 공고 목록과 다음 페이지 정보를 담은 {@link PostListResponse}
   */
  @Transactional(readOnly = true)
  public PostResponseDto.PostListResponse getPostList(
      PostListRequest request,
      PostCursor cursor,
      LocalDate referenceDate,
      LocalDateTime now
  ) {
    // 검색 조건에 맞는 공고 목록 조회
    List<Post> fetchedPosts = postRepository.findPosts(
        request,
        cursor,
        referenceDate,
        now
    );

    // 조회 결과를 목록 응답으로 변환해 반환
    return toPostListResponse(fetchedPosts, request, referenceDate);
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

  /**
   * UTC 기준 저장 시각을 동일한 순간의 +09:00 응답 시각으로 변환한다.
   *
   * @param utcDateTime UTC 기준 저장 시각
   * @return +09:00 오프셋의 {@link OffsetDateTime}, 입력이 null이면 null
   */
  private OffsetDateTime toResponseDateTime(LocalDateTime utcDateTime) {
    // 마감 시각이나 종료 시각이 없는 경우 그대로 유지
    if (utcDateTime == null) return null;

    // 저장된 시각이 UTC 기준임을 명시
    OffsetDateTime utcOffsetDateTime = utcDateTime.atOffset(ZoneOffset.UTC);

    // 동일한 순간을 +09:00 시각으로 표현
    return utcOffsetDateTime.withOffsetSameInstant(ZoneOffset.ofHours(9));
  }

  /**
   * 반환할 공고들의 대표 이미지 URL을 한 번에 조회한다.
   *
   * @param posts 실제 응답에 포함할 공고 목록
   * @return 공고 ID를 키로 하고 대표 이미지 URL을 값으로 하는 {@link Map}
   * @throws IllegalStateException 한 공고에 대표 이미지가 여러 개 존재하는 경우
   */
  private Map<Long, String> getRepresentativeImageUrls(List<Post> posts) {
    // posts가 비어 있으면 Map.of() 반환
    if (posts.isEmpty()) {
      return Map.of();
    }

    // 공고 ID를 담을 목록 생성
    List<Long> postIds = new ArrayList<>();

    // posts를 순회하며 각 공고의 getPostId()를 postIds에 추가
    for (Post post : posts) {
      postIds.add(post.getPostId());
    }

    // 대표 이미지를 한 번에 조회
    List<RepresentativeImageView> images = postImageRepository.findRepresentativeImagesByPostIds(postIds);

    // 결과를 담을 Map 생성
    Map<Long, String> imageUrls = new HashMap<>();

    // images 순회
    for (RepresentativeImageView image : images) {
      Long postId = image.getPostId();

      if (imageUrls.containsKey(postId)) {
        throw new IllegalStateException(
            "공고에 대표 이미지가 여러 개 존재합니다. postId=" + postId
        );
      }

      imageUrls.put(postId, image.getImageUrl());
    }

    // imageUrls 반환
    return imageUrls;
  }

  /**
   * 공고와 대표 이미지 URL을 목록 항목 응답으로 변환한다.
   * UTC 저장 시각은 동일한 순간의 +09:00 시각으로 변환한다.
   *
   * @param post 목록에 포함할 공고
   * @param representativeImageUrl 대표 이미지 URL. 이미지가 없으면 null
   * @return 공고 한 건의 목록 응답인 {@link PostListItemResponse}
   */
  private PostResponseDto.PostListItemResponse toPostListItemResponse(
      Post post,
      String representativeImageUrl
  ) {
    // 접수 마감 시각을 응답 시각으로 변환
    OffsetDateTime recruitmentDeadline = toResponseDateTime(post.getRecruitmentDeadline());

    // 생성 시각을 응답 시각으로 변환
    OffsetDateTime createdAt = toResponseDateTime(post.getCreatedAt());

    // 종료 시각을 응답 시각으로 변환
    OffsetDateTime closedAt = toResponseDateTime(post.getClosedAt());

    // PostListItemResponse를 생성해 반환
    return new PostResponseDto.PostListItemResponse(
        post.getPostId(),
        post.getTitle(),
        post.getNoticeType(),
        post.getSpecies(),
        post.getBreed(),
        post.getEstimatedBirthDate(),
        post.getGender(),
        post.getRegion(),
        post.getDistrict(),
        representativeImageUrl,
        post.getStatus(),
        recruitmentDeadline,
        post.getBookmarkCount(),
        createdAt,
        post.getClosedReason(),
        closedAt
    );
  }

  /**
   * 조회 결과를 실제 반환 목록과 다음 페이지 정보로 변환한다.
   *
   * @param fetchedPosts 정렬된 공고 조회 결과. 최대 size + 1개
   * @param request 검증된 목록 조회 요청
   * @param referenceDate 첫 페이지부터 유지하는 나이 계산 기준일
   * @return 공고 목록과 다음 페이지 정보를 담은 {@link PostListResponse}
   */
  private PostResponseDto.PostListResponse toPostListResponse(
      List<Post> fetchedPosts,
      PostListRequest request,
      LocalDate referenceDate
  ) {
    // 요청한 조회 개수를 저장
    int size = request.size();

    // 다음 페이지 존재 여부 판단
    boolean hasNext = false;

    if (fetchedPosts.size() > size) {
      hasNext = true;
    }

    // 실제 반환할 목록을 별도로 복사
    List<Post> posts;

    if (hasNext) {
      posts = new ArrayList<>(fetchedPosts.subList(0, size));
    } else {
      posts = new ArrayList<>(fetchedPosts);
    }

    // 실제 반환 목록의 대표 이미지 조회
    Map<Long, String> imageUrls = getRepresentativeImageUrls(posts);

    // 응답 항목 목록 생성
    List<PostListItemResponse> responses = new ArrayList<>();

    // posts를 순서대로 순회하며 응답 항목 추가
    for (Post post : posts) {
      responses.add(toPostListItemResponse(post, imageUrls.get(post.getPostId())));
    }

    // 다음 커서를 null로 초기화
    String nextCursor = null;

    // hasNext가 true일 때만 다음 커서 생성
    if (hasNext) {
      Post lastPost = posts.getLast();
      PostCursor nextPostCursor = postCursorFactory.create(lastPost, request, referenceDate);
      nextCursor = postCursorCodec.encode(nextPostCursor);
    }

    // PostListResponse를 생성해 반환
    return new PostResponseDto.PostListResponse(
        responses,
        nextCursor,
        hasNext
    );
  }
}
