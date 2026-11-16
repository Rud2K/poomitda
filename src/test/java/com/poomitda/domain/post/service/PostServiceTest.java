package com.poomitda.domain.post.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.poomitda.domain.matching.repository.ApplicationRepository;
import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.entity.constant.*;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.domain.post.cursor.*;
import com.poomitda.domain.post.dto.PostRequestDto.*;
import com.poomitda.domain.post.dto.PostResponseDto;
import com.poomitda.domain.post.dto.PostResponseDto.*;
import com.poomitda.domain.post.dto.constant.PostStatusFilter;
import com.poomitda.domain.post.entity.*;
import com.poomitda.domain.post.entity.constant.*;
import com.poomitda.domain.post.exception.PostErrorCode;
import com.poomitda.domain.post.repository.*;
import com.poomitda.domain.post.repository.PostImageRepository.RepresentativeImageView;
import com.poomitda.domain.post.validation.PostCreateValidator;
import com.poomitda.global.constant.*;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import com.poomitda.global.validation.DistrictCatalog;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PostServiceTest {

  private final MemberRepository memberRepository = mock(MemberRepository.class);

  private final ImageUploadRepository imageUploadRepository = mock(ImageUploadRepository.class);

  private final PostRepository postRepository = mock(PostRepository.class);

  private final PostImageRepository postImageRepository = mock(PostImageRepository.class);

  private final BookmarkRepository bookmarkRepository = mock(BookmarkRepository.class);

  private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);

  private final PostCursorFactory postCursorFactory = mock(PostCursorFactory.class);

  private final PostCursorCodec postCursorCodec = mock(PostCursorCodec.class);

  private final PostService postService = new PostService(
      memberRepository,
      imageUploadRepository,
      postRepository,
      postImageRepository,
      new PostCreateValidator(new DistrictCatalog()),
      bookmarkRepository,
      applicationRepository,
      postCursorFactory,
      postCursorCodec
  );

  private static final Long MEMBER_ID = 1L;
  private static final Long POST_ID = 100L;

  private static final String IMAGE_URL_1 = "https://example.com/posts/image1.png";

  private static final String IMAGE_URL_2 = "https://example.com/posts/image2.png";

  @Test
  @DisplayName("정상 공고 등록 -> 공고 및 이미지 저장과 업로드 이력 연결")
  void createsPostAndAttachesImages() {
    // given
    Member author = prepareAuthor(
        MemberStatus.ACTIVE,
        MemberRole.ROLE_PROTECTOR
    );

    ImageUpload firstUpload = new ImageUpload(author, "posts/1/image1.png", IMAGE_URL_1);

    ImageUpload secondUpload = new ImageUpload(author, "posts/1/image2.png", IMAGE_URL_2);

    // 조회 결과 순서를 요청 순서와 다르게 구성한다.
    when(imageUploadRepository.findAllByImageUrlInForUpdate(List.of(IMAGE_URL_1, IMAGE_URL_2)))
        .thenReturn(List.of(secondUpload, firstUpload));

    Post savedPost = prepareSavedPost();

    CreatePostRequest request = createRequest(
        OffsetDateTime.parse("2030-01-01T01:30:00+09:00"),
        List.of(
            new PostImageRequest(IMAGE_URL_1, false, 5),
            new PostImageRequest(IMAGE_URL_2, true, 0)
        )
    );

    // when
    CreatePostResponse response = postService.createPost(MEMBER_ID, request);

    // then
    assertEquals(POST_ID, response.postId());

    ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
    verify(postRepository).save(postCaptor.capture());

    Post createdPost = postCaptor.getValue();

    assertAll(
        () -> assertSame(author, createdPost.getAuthor()),
        () -> assertEquals(request.title(), createdPost.getTitle()),
        () -> assertEquals(request.content(), createdPost.getContent()),
        () -> assertEquals(request.noticeType(), createdPost.getNoticeType()),
        () -> assertEquals(request.species(), createdPost.getSpecies()),
        () -> assertEquals(request.breed(), createdPost.getBreed()),
        () -> assertEquals(
            request.estimatedBirthDate(),
            createdPost.getEstimatedBirthDate()
        ),
        () -> assertEquals(request.gender(), createdPost.getGender()),
        () -> assertEquals(
            request.isNeutered().booleanValue(),
            createdPost.isNeutered()
        ),
        () -> assertNull(createdPost.getFosterPeriod()),
        () -> assertEquals(
            request.foundLocation(),
            createdPost.getFoundLocation()
        ),
        () -> assertEquals(request.region(), createdPost.getRegion()),
        () -> assertEquals(request.district(), createdPost.getDistrict()),
        () -> assertEquals(PostStatus.RECRUITING, createdPost.getStatus()),
        () -> assertEquals(0, createdPost.getBookmarkCount()),
        () -> assertNull(createdPost.getClosedAt()),
        () -> assertNull(createdPost.getClosedReason()),
        () -> assertEquals(
            LocalDateTime.of(2029, 12, 31, 16, 30),
            createdPost.getRecruitmentDeadline()
        )
    );

    ArgumentCaptor<Iterable<PostImage>> imagesCaptor = ArgumentCaptor.forClass(Iterable.class);

    verify(postImageRepository).saveAll(imagesCaptor.capture());

    List<PostImage> savedImages = new ArrayList<>();
    imagesCaptor.getValue().forEach(savedImages::add);

    assertEquals(2, savedImages.size());

    PostImage firstImage = savedImages.get(0);
    PostImage secondImage = savedImages.get(1);

    assertAll(
        () -> assertSame(savedPost, firstImage.getPost()),
        () -> assertEquals(IMAGE_URL_1, firstImage.getImageUrl()),
        () -> assertFalse(firstImage.isRepresentative()),
        () -> assertEquals(5, firstImage.getDisplayOrder()),
        () -> assertSame(savedPost, secondImage.getPost()),
        () -> assertEquals(IMAGE_URL_2, secondImage.getImageUrl()),
        () -> assertTrue(secondImage.isRepresentative()),
        () -> assertEquals(0, secondImage.getDisplayOrder()),
        () -> assertEquals(
            ImageUploadStatus.ATTACHED,
            firstUpload.getStatus()
        ),
        () -> assertEquals(
            ImageUploadStatus.ATTACHED,
            secondUpload.getStatus()
        )
    );
  }

  @Test
  @DisplayName("접수 마감 시각 없음 -> null 저장")
  void preservesNullDeadline() {
    // given
    Member author = prepareAuthor(
        MemberStatus.ACTIVE,
        MemberRole.ROLE_PROTECTOR
    );
    preparePendingUpload(author);
    prepareSavedPost();

    // when
    postService.createPost(MEMBER_ID, validRequest());

    // then
    ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
    verify(postRepository).save(captor.capture());

    assertNull(captor.getValue().getRecruitmentDeadline());
  }

  @Test
  @DisplayName("입력값 조합 오류 -> DB 조회 및 저장 중단")
  void rejectsInvalidInputBeforeDatabaseAccess() {
    // given
    CreatePostRequest request = createRequest(
        null,
        List.of(new PostImageRequest(IMAGE_URL_1, false, 0))
    );

    // when & then
    assertRejected(request, CommonErrorCode.INVALID_INPUT_VALUE);

    verifyNoInteractions(memberRepository, imageUploadRepository);
  }

  @Test
  @DisplayName("회원 없음 -> 인증 오류 및 저장 중단")
  void rejectsMissingMember() {
    // given
    when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.empty());

    // when & then
    assertRejected(validRequest(), CommonErrorCode.UNAUTHORIZED);

    verifyNoInteractions(imageUploadRepository);
  }

  @Test
  @DisplayName("탈퇴한 회원 -> 인증 오류 및 저장 중단")
  void rejectsDeletedMember() {
    // given
    prepareAuthor(MemberStatus.DELETED, MemberRole.ROLE_PROTECTOR);

    // when & then
    assertRejected(validRequest(), CommonErrorCode.UNAUTHORIZED);

    verifyNoInteractions(imageUploadRepository);
  }

  @ParameterizedTest
  @EnumSource(
      value = MemberRole.class,
      names = {"ROLE_USER", "ROLE_ADMIN"}
  )
  @DisplayName("보호자가 아닌 회원 -> 권한 오류 및 저장 중단")
  void rejectsNonProtector(MemberRole role) {
    // given
    prepareAuthor(MemberStatus.ACTIVE, role);

    // when & then
    assertRejected(validRequest(), CommonErrorCode.FORBIDDEN);

    verifyNoInteractions(imageUploadRepository);
  }

  @Test
  @DisplayName("업로드 이력 누락 -> 입력값 오류 및 저장 중단")
  void rejectsMissingUpload() {
    // given
    Member author = prepareAuthor(
        MemberStatus.ACTIVE,
        MemberRole.ROLE_PROTECTOR
    );

    ImageUpload upload = new ImageUpload(author, "posts/1/image1.png", IMAGE_URL_1);

    when(imageUploadRepository.findAllByImageUrlInForUpdate(List.of(IMAGE_URL_1, IMAGE_URL_2)))
        .thenReturn(List.of(upload));

    CreatePostRequest request = createRequest(
        null,
        List.of(
            new PostImageRequest(IMAGE_URL_1, true, 0),
            new PostImageRequest(IMAGE_URL_2, false, 1)
        )
    );

    // when & then
    assertRejected(request, CommonErrorCode.INVALID_INPUT_VALUE);

    assertEquals(ImageUploadStatus.PENDING, upload.getStatus());
  }

  @Test
  @DisplayName("다른 회원의 이미지 사용 -> 입력값 오류 및 저장 중단")
  void rejectsAnotherMembersUpload() {
    // given
    prepareAuthor(MemberStatus.ACTIVE, MemberRole.ROLE_PROTECTOR);

    Member otherMember = mock(Member.class);
    when(otherMember.getMemberId()).thenReturn(2L);

    ImageUpload upload = preparePendingUpload(otherMember);

    // when & then
    assertRejected(validRequest(), CommonErrorCode.INVALID_INPUT_VALUE);

    assertEquals(ImageUploadStatus.PENDING, upload.getStatus());
  }

  @Test
  @DisplayName("이미 연결된 이미지 사용 -> 입력값 오류 및 저장 중단")
  void rejectsAttachedUpload() {
    // given
    Member author = prepareAuthor(
        MemberStatus.ACTIVE,
        MemberRole.ROLE_PROTECTOR
    );

    ImageUpload upload = preparePendingUpload(author);
    upload.attachToPost();

    // when & then
    assertRejected(validRequest(), CommonErrorCode.INVALID_INPUT_VALUE);

    assertEquals(ImageUploadStatus.ATTACHED, upload.getStatus());
  }

  @Test
  @DisplayName("공고 이미지 저장 실패 -> 기존 예외 전달 및 상태 전환 중단")
  void propagatesImageSaveFailure() {
    // given
    Member author = prepareAuthor(
        MemberStatus.ACTIVE,
        MemberRole.ROLE_PROTECTOR
    );
    ImageUpload upload = preparePendingUpload(author);
    prepareSavedPost();

    RuntimeException failure = new RuntimeException("이미지 저장 실패");

    doThrow(failure)
        .when(postImageRepository)
        .saveAll(anyList());

    // when
    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> postService.createPost(MEMBER_ID, validRequest())
    );

    // then
    assertSame(failure, exception);
    assertEquals(ImageUploadStatus.PENDING, upload.getStatus());
  }

  @Test
  @DisplayName("조회 가능한 공고 없음 -> 공고 없음 오류 및 후속 조회 중단")
  void getPostDetailPostNotFoundTest() {
    // given
    Long memberId = 1L;
    Long postId = 2L;
    when(postRepository.findByPostIdAndDeletedAtIsNull(postId)).thenReturn(Optional.empty());

    // when & then
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> postService.getPostDetail(memberId, postId)
    );

    assertEquals(PostErrorCode.POST_NOT_FOUND, exception.getErrorCode());

    verify(postRepository).findByPostIdAndDeletedAtIsNull(postId);
    verifyNoInteractions(bookmarkRepository, postImageRepository, applicationRepository);
  }

  @Test
  @DisplayName("타인 공고에 신청 이력과 마감일이 없음 -> 상세 정보와 신청 가능 반환")
  void getPostDetailWithoutDeadlineTest() {
    // given
    Long memberId = 1L;
    Long authorId = 2L;
    Long postId = 3L;

    Member author = new Member(
        "author@example.com",
        "encodedPassword",
        "구리보호소",
        null,
        null
    );
    ReflectionTestUtils.setField(author, "memberId", authorId);

    Post post = new Post(
        author,
        "고양이의 가족을 찾습니다.",
        "공고 상세 조회 테스트입니다.",
        NoticeType.ADOPTION,
        Species.CAT,
        "코리안 숏헤어",
        LocalDate.of(2026, 5, 1),
        Gender.FEMALE,
        true,
        null,
        "인창동 공원",
        Region.GYEONGGI,
        "구리시",
        null
    );
    ReflectionTestUtils.setField(
        post,
        "postId",
        postId
    );
    ReflectionTestUtils.setField(
        post,
        "createdAt",
        LocalDateTime.of(2026, 11, 1, 3, 0)
    );
    ReflectionTestUtils.setField(
        post,
        "updatedAt",
        LocalDateTime.of(2026, 11, 2, 4, 30)
    );

    when(postRepository.findByPostIdAndDeletedAtIsNull(postId))
        .thenReturn(Optional.of(post));

    when(bookmarkRepository.existsByMember_MemberIdAndPost_PostId(memberId, postId))
        .thenReturn(true);

    when(applicationRepository.existsByPost_PostIdAndApplicant_MemberId(postId, memberId))
        .thenReturn(false);

    when(postImageRepository.findAllByPost_PostIdOrderByDisplayOrderAsc(postId))
        .thenReturn(List.of());

    // when
    PostResponseDto.PostDetailResponse response = postService.getPostDetail(memberId, postId);

    // then
    assertAll(
        () -> assertEquals(postId, response.postId()),
        () -> assertEquals("고양이의 가족을 찾습니다.", response.title()),
        () -> assertEquals(authorId, response.writer().memberId()),
        () -> assertEquals("구리보호소", response.writer().nickname()),
        () -> assertNull(response.writer().averageRating()),
        () -> assertEquals(0, response.writer().reviewCount()),
        () -> assertEquals(PostStatus.RECRUITING, response.status()),

        () -> assertFalse(response.isWriter()),
        () -> assertTrue(response.isBookmarked()),
        () -> assertTrue(response.canApply()),

        () -> assertNull(response.recruitmentDeadline()),
        () -> assertNull(response.closedAt()),
        () -> assertNull(response.closedReason()),
        () -> assertTrue(response.images().isEmpty()),

        () -> assertEquals(
            OffsetDateTime.parse("2026-11-01T12:00:00+09:00"),
            response.createdAt()
        ),
        () -> assertEquals(
            OffsetDateTime.parse("2026-11-02T13:30:00+09:00"),
            response.updatedAt()
        )
    );

    verify(postRepository).findByPostIdAndDeletedAtIsNull(postId);
    verify(bookmarkRepository).existsByMember_MemberIdAndPost_PostId(memberId, postId);
    verify(applicationRepository).existsByPost_PostIdAndApplicant_MemberId(postId, memberId);
    verify(postImageRepository).findAllByPost_PostIdOrderByDisplayOrderAsc(postId);
  }

  @Test
  @DisplayName("공고 이미지 두 개 조회 -> 이미지 정보와 조회 순서를 유지하여 반환")
  void getPostDetailImagesTest() {
    // given
    Long memberId = 1L;
    Long authorId = 2L;
    Long postId = 3L;

    Member author = new Member(
        "author@example.com",
        "encodedPassword",
        "구리보호소",
        null,
        null
    );
    ReflectionTestUtils.setField(author, "memberId", authorId);

    Post post = new Post(
        author,
        "고양이의 가족을 찾습니다.",
        "공고 상세 조회 테스트입니다.",
        NoticeType.ADOPTION,
        Species.CAT,
        "코리안 숏헤어",
        LocalDate.of(2026, 5, 1),
        Gender.FEMALE,
        true,
        null,
        "인창동 공원",
        Region.GYEONGGI,
        "구리시",
        null
    );
    ReflectionTestUtils.setField(
        post, "postId", postId
    );
    ReflectionTestUtils.setField(
        post, "createdAt", LocalDateTime.of(2026, 11, 1, 3, 0)
    );
    ReflectionTestUtils.setField(
        post, "updatedAt", LocalDateTime.of(2026, 11, 2, 4, 30)
    );

    PostImage firstImage = new PostImage(post, IMAGE_URL_1, false, 0);
    PostImage secondImage = new PostImage(post, IMAGE_URL_2, true, 1);

    ReflectionTestUtils.setField(firstImage, "postImageId", 101L);
    ReflectionTestUtils.setField(secondImage, "postImageId", 102L);

    when(postRepository.findByPostIdAndDeletedAtIsNull(postId))
        .thenReturn(Optional.of(post));

    when(bookmarkRepository.existsByMember_MemberIdAndPost_PostId(memberId, postId))
        .thenReturn(false);

    when(applicationRepository.existsByPost_PostIdAndApplicant_MemberId(postId, memberId))
        .thenReturn(false);

    when(postImageRepository.findAllByPost_PostIdOrderByDisplayOrderAsc(postId))
        .thenReturn(List.of(firstImage, secondImage));

    // when
    PostResponseDto.PostDetailResponse response = postService.getPostDetail(memberId, postId);

    // then
    List<PostResponseDto.PostImageResponse> expectedImages = List.of(
        new PostResponseDto.PostImageResponse(
            101L, IMAGE_URL_1, false, 0
        ),
        new PostResponseDto.PostImageResponse(
            102L, IMAGE_URL_2, true, 1
        )
    );

    assertEquals(expectedImages, response.images());

    verify(postImageRepository).findAllByPost_PostIdOrderByDisplayOrderAsc(postId);
  }

  @Test
  @DisplayName("본인 공고 조회 -> 작성자 여부는 true이고 신청 가능 여부는 false")
  void getPostDetailOwnPostTest() {
    // given
    Long memberId = 1L;
    Long postId = 2L;

    Member author = new Member(
        "author@example.com",
        "encodedPassword",
        "구리보호소",
        null,
        null
    );
    ReflectionTestUtils.setField(author, "memberId", memberId);

    Post post = new Post(
        author,
        "고양이의 가족을 찾습니다.",
        "본인 공고 상세 조회 테스트입니다.",
        NoticeType.ADOPTION,
        Species.CAT,
        "코리안 숏헤어",
        LocalDate.of(2026, 5, 1),
        Gender.FEMALE,
        true,
        null,
        "인창동 공원",
        Region.GYEONGGI,
        "구리시",
        null
    );
    ReflectionTestUtils.setField(
        post, "postId", postId
    );
    ReflectionTestUtils.setField(
        post, "createdAt", LocalDateTime.of(2026, 11, 1, 3, 0)
    );
    ReflectionTestUtils.setField(
        post, "updatedAt", LocalDateTime.of(2026, 11, 2, 4, 30)
    );

    when(postRepository.findByPostIdAndDeletedAtIsNull(postId))
        .thenReturn(Optional.of(post));

    when(bookmarkRepository.existsByMember_MemberIdAndPost_PostId(memberId, postId))
        .thenReturn(false);

    when(postImageRepository.findAllByPost_PostIdOrderByDisplayOrderAsc(postId))
        .thenReturn(List.of());

    when(applicationRepository.existsByPost_PostIdAndApplicant_MemberId(postId, memberId))
        .thenReturn(false);

    // when
    PostResponseDto.PostDetailResponse response = postService.getPostDetail(memberId, postId);

    // then
    assertAll(
        () -> assertEquals(postId, response.postId()),
        () -> assertEquals(memberId, response.writer().memberId()),
        () -> assertTrue(response.isWriter()),
        () -> assertFalse(response.canApply()),
        () -> assertEquals(PostStatus.RECRUITING, response.status()),
        () -> assertNull(response.recruitmentDeadline())
    );

    verify(postRepository).findByPostIdAndDeletedAtIsNull(postId);
  }

  @Test
  @DisplayName("타인 공고에 기존 신청 이력 존재 -> 신청 불가 반환")
  void getPostDetailAlreadyAppliedTest() {
    // given
    Long memberId = 1L;
    Long authorId = 2L;
    Long postId = 3L;

    Member author = new Member(
        "author@example.com",
        "encodedPassword",
        "구리보호소",
        null,
        null
    );
    ReflectionTestUtils.setField(author, "memberId", authorId);

    Post post = new Post(
        author,
        "고양이의 가족을 찾습니다.",
        "기존 신청 이력이 있는 공고 조회 테스트입니다.",
        NoticeType.ADOPTION,
        Species.CAT,
        "코리안 숏헤어",
        LocalDate.of(2026, 5, 1),
        Gender.FEMALE,
        true,
        null,
        "인창동 공원",
        Region.GYEONGGI,
        "구리시",
        null
    );
    ReflectionTestUtils.setField(
        post, "postId", postId
    );
    ReflectionTestUtils.setField(
        post, "createdAt", LocalDateTime.of(2026, 11, 1, 3, 0)
    );
    ReflectionTestUtils.setField(
        post, "updatedAt", LocalDateTime.of(2026, 11, 2, 4, 30)
    );

    when(postRepository.findByPostIdAndDeletedAtIsNull(postId))
        .thenReturn(Optional.of(post));

    when(bookmarkRepository.existsByMember_MemberIdAndPost_PostId(memberId, postId))
        .thenReturn(false);

    when(postImageRepository.findAllByPost_PostIdOrderByDisplayOrderAsc(postId))
        .thenReturn(List.of());

    when(applicationRepository.existsByPost_PostIdAndApplicant_MemberId(postId, memberId))
        .thenReturn(true);

    // when
    PostResponseDto.PostDetailResponse response = postService.getPostDetail(memberId, postId);

    // then
    assertAll(
        () -> assertEquals(postId, response.postId()),
        () -> assertEquals(authorId, response.writer().memberId()),
        () -> assertFalse(response.isWriter()),
        () -> assertFalse(response.canApply()),
        () -> assertEquals(PostStatus.RECRUITING, response.status()),
        () -> assertNull(response.recruitmentDeadline())
    );

    verify(applicationRepository).existsByPost_PostIdAndApplicant_MemberId(postId, memberId);
  }

  @ParameterizedTest
  @EnumSource(
      value = PostStatus.class,
      names = {"RECEPTION_CLOSED", "CLOSED"}
  )
  @DisplayName("접수 중이 아닌 공고 조회 -> 상세 정보와 신청 불가 반환")
  void getPostDetailNotRecruitingTest(PostStatus status) {
    // given
    Long memberId = 1L;
    Long authorId = 2L;
    Long postId = 3L;

    Member author = new Member(
        "author@example.com",
        "encodedPassword",
        "구리보호소",
        null,
        null
    );
    ReflectionTestUtils.setField(author, "memberId", authorId);

    Post post = new Post(
        author,
        "고양이의 가족을 찾습니다.",
        "접수 중이 아닌 공고 조회 테스트입니다.",
        NoticeType.ADOPTION,
        Species.CAT,
        "코리안 숏헤어",
        LocalDate.of(2026, 5, 1),
        Gender.FEMALE,
        true,
        null,
        "인창동 공원",
        Region.GYEONGGI,
        "구리시",
        null
    );
    ReflectionTestUtils.setField(
        post, "postId", postId
    );
    ReflectionTestUtils.setField(
        post, "status", status
    );
    ReflectionTestUtils.setField(
        post, "createdAt", LocalDateTime.of(2026, 11, 1, 3, 0)
    );
    ReflectionTestUtils.setField(
        post, "updatedAt", LocalDateTime.of(2026, 11, 2, 4, 30)
    );

    if (status == PostStatus.CLOSED) {
      ReflectionTestUtils.setField(
          post, "closedReason", PostClosedReason.MANUAL
      );
      ReflectionTestUtils.setField(
          post, "closedAt", LocalDateTime.of(2026, 11, 2, 4, 30)
      );
    }

    when(postRepository.findByPostIdAndDeletedAtIsNull(postId))
        .thenReturn(Optional.of(post));

    when(bookmarkRepository.existsByMember_MemberIdAndPost_PostId(memberId, postId))
        .thenReturn(false);

    when(postImageRepository.findAllByPost_PostIdOrderByDisplayOrderAsc(postId))
        .thenReturn(List.of());

    when(applicationRepository.existsByPost_PostIdAndApplicant_MemberId(postId, memberId))
        .thenReturn(false);

    // when
    PostResponseDto.PostDetailResponse response = postService.getPostDetail(memberId, postId);

    // then
    assertAll(
        () -> assertEquals(postId, response.postId()),
        () -> assertEquals(status, response.status()),
        () -> assertFalse(response.isWriter()),
        () -> assertFalse(response.canApply()),
        () -> assertNull(response.recruitmentDeadline())
    );

    if (status == PostStatus.CLOSED) {
      assertAll(
          () -> assertEquals(
              PostClosedReason.MANUAL,
              response.closedReason()
          ),
          () -> assertEquals(
              OffsetDateTime.parse("2026-11-02T13:30:00+09:00"),
              response.closedAt()
          )
      );
    } else {
      assertAll(
          () -> assertNull(response.closedReason()),
          () -> assertNull(response.closedAt())
      );
    }

    verify(postRepository).findByPostIdAndDeletedAtIsNull(postId);
  }

  @ParameterizedTest
  @CsvSource({
      "-1, false",
      "1, true"
  })
  @DisplayName("마감 시각이 있는 접수 중 공고 조회 -> 마감 전후에 맞는 신청 가능 여부 반환")
  void getPostDetailDeadlineTest(int deadlineOffsetDays, boolean expectedCanApply) {
    // given
    Long memberId = 1L;
    Long authorId = 2L;
    Long postId = 100L;

    Member author = new Member(
        "author@example.com",
        "encodedPassword",
        "구리보호소",
        null,
        null
    );
    ReflectionTestUtils.setField(author, "memberId", authorId);

    LocalDateTime recruitmentDeadline = LocalDateTime.now(ZoneOffset.UTC).plusDays(deadlineOffsetDays);

    Post post = new Post(
        author,
        "고양이의 가족을 찾습니다.",
        "마감 시각에 따른 신청 가능 여부 테스트입니다.",
        NoticeType.ADOPTION,
        Species.CAT,
        "코리안 숏헤어",
        LocalDate.of(2026, 5, 1),
        Gender.FEMALE,
        true,
        null,
        "인창동 공원",
        Region.GYEONGGI,
        "구리시",
        recruitmentDeadline
    );
    ReflectionTestUtils.setField(
        post, "postId", postId
    );
    ReflectionTestUtils.setField(
        post, "createdAt", LocalDateTime.of(2026, 11, 1, 3, 0)
    );
    ReflectionTestUtils.setField(
        post, "updatedAt", LocalDateTime.of(2026, 11, 2, 4, 30)
    );

    when(postRepository.findByPostIdAndDeletedAtIsNull(postId))
        .thenReturn(Optional.of(post));

    when(bookmarkRepository.existsByMember_MemberIdAndPost_PostId(memberId, postId))
        .thenReturn(false);

    when(postImageRepository.findAllByPost_PostIdOrderByDisplayOrderAsc(postId))
        .thenReturn(List.of());

    when(applicationRepository.existsByPost_PostIdAndApplicant_MemberId(postId, memberId))
        .thenReturn(false);

    // when
    PostResponseDto.PostDetailResponse response = postService.getPostDetail(memberId, postId);

    // then
    assertNotNull(response.recruitmentDeadline());

    assertAll(
        () -> assertFalse(response.isWriter()),
        () -> assertEquals(PostStatus.RECRUITING, response.status()),
        () -> assertEquals(expectedCanApply, response.canApply()),

        () -> assertEquals(
            ZoneOffset.ofHours(9),
            response.recruitmentDeadline().getOffset()
        ),

        () -> assertEquals(
            recruitmentDeadline.toInstant(ZoneOffset.UTC),
            response.recruitmentDeadline().toInstant()
        )
    );

    verify(applicationRepository).existsByPost_PostIdAndApplicant_MemberId(postId, memberId);
  }

  @ParameterizedTest
  @MethodSource("validRegionCases")
  @DisplayName("유효한 지역 조합 또는 시/군/구 생략 -> 정리된 지역 정보 저장")
  void createsPostWithValidRegion(
      Region region,
      String district,
      String expectedDistrict
  ) {
    // given
    CreatePostRequest request = createRequestWithRegion(region, district);

    Member author = prepareAuthor(
        MemberStatus.ACTIVE,
        MemberRole.ROLE_PROTECTOR
    );
    preparePendingUpload(author);
    prepareSavedPost();

    // when
    CreatePostResponse response = postService.createPost(MEMBER_ID, request);

    // then
    assertEquals(POST_ID, response.postId());

    ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
    verify(postRepository).save(captor.capture());

    Post createdPost = captor.getValue();

    assertAll(
        () -> assertEquals(region, createdPost.getRegion()),
        () -> assertEquals(expectedDistrict, createdPost.getDistrict())
    );
  }

  @ParameterizedTest
  @MethodSource("invalidRegionCases")
  @DisplayName("지역 누락 또는 잘못된 시/군/구 조합 -> 입력값 오류 및 DB 접근 중단")
  void rejectsInvalidRegionBeforeDatabaseAccess(
      Region region,
      String district
  ) {
    // given
    CreatePostRequest request = createRequestWithRegion(region, district);

    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> postService.createPost(MEMBER_ID, request)
    );

    // then
    assertEquals(
        CommonErrorCode.INVALID_INPUT_VALUE,
        exception.getErrorCode()
    );

    verifyNoInteractions(
        memberRepository,
        imageUploadRepository,
        postRepository,
        postImageRepository,
        bookmarkRepository,
        applicationRepository
    );
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  @DisplayName("조회 결과가 요청 개수 이하 -> 전체 반환 및 다음 커서 없음")
  void returnsLastPageWithoutCursor(int postCount) {
    // given
    PostListRequest request = createListRequest(2);
    LocalDate referenceDate = LocalDate.of(2026, 11, 16);
    LocalDateTime now = referenceDate.atTime(10, 0);

    List<Post> fetchedPosts = new ArrayList<>();
    List<Long> expectedIds = new ArrayList<>();

    for (int i = 0; i < postCount; i++) {
      Long postId = 100L - i;
      fetchedPosts.add(createListPost(postId));
      expectedIds.add(postId);
    }

    when(postRepository.findPosts(
        request, null, referenceDate, now
    )).thenReturn(fetchedPosts);

    if (postCount > 0) {
      when(postImageRepository.findRepresentativeImagesByPostIds(
          expectedIds
      )).thenReturn(List.of());
    }

    // when
    PostListResponse response = postService.getPostList(
        request, null, referenceDate, now
    );

    // then
    assertEquals(
        expectedIds,
        response.posts().stream().map(PostListItemResponse::postId).toList()
    );
    assertFalse(response.hasNext());
    assertNull(response.nextCursor());

    assertEquals(postCount, fetchedPosts.size());

    verify(postRepository).findPosts(
        request, null, referenceDate, now
    );

    if (postCount == 0) {
      verifyNoInteractions(postImageRepository);
    } else {
      verify(postImageRepository).findRepresentativeImagesByPostIds(expectedIds);
      verifyNoMoreInteractions(postImageRepository);

      assertTrue(response.posts().stream()
          .allMatch(item -> item.representativeImageUrl() == null));
    }

    verifyNoInteractions(postCursorFactory, postCursorCodec);
  }

  @Test
  @DisplayName("조회 결과가 요청 개수 초과 -> 여분 제외 및 마지막 반환 공고로 커서 생성")
  void createsCursorFromLastReturnedPost() {
    // given
    PostListRequest request = createListRequest(2);
    LocalDate referenceDate = LocalDate.of(2026, 11, 16);
    LocalDateTime now = referenceDate.atTime(10, 0);

    Post firstPost = createListPost(100L);
    Post secondPost = createListPost(99L);
    Post extraPost = createListPost(98L);

    List<Post> fetchedPosts = new ArrayList<>(
        List.of(firstPost, secondPost, extraPost)
    );

    PostCursor nextPostCursor = mock(PostCursor.class);

    when(postRepository.findPosts(
        request, null, referenceDate, now
    )).thenReturn(fetchedPosts);

    when(postImageRepository.findRepresentativeImagesByPostIds(
        List.of(100L, 99L)
    )).thenReturn(List.of());

    when(postCursorFactory.create(
        secondPost, request, referenceDate
    )).thenReturn(nextPostCursor);

    when(postCursorCodec.encode(nextPostCursor))
        .thenReturn("encoded-next-cursor");

    // when
    PostListResponse response = postService.getPostList(
        request, null, referenceDate, now
    );

    // then
    assertEquals(
        List.of(100L, 99L),
        response.posts().stream().map(PostListItemResponse::postId).toList()
    );
    assertTrue(response.hasNext());
    assertEquals("encoded-next-cursor", response.nextCursor());

    assertEquals(
        List.of(firstPost, secondPost, extraPost),
        fetchedPosts
    );

    verify(postRepository).findPosts(
        request, null, referenceDate, now
    );

    verify(postImageRepository).findRepresentativeImagesByPostIds(
        List.of(100L, 99L)
    );
    verifyNoMoreInteractions(postImageRepository);

    verify(postCursorFactory).create(
        secondPost, request, referenceDate
    );
    verify(postCursorCodec).encode(nextPostCursor);
    verifyNoMoreInteractions(postCursorFactory, postCursorCodec);
  }

  @Test
  @DisplayName("대표 이미지 조회 순서가 다름 -> 공고 ID로 연결하고 목록 순서 유지")
  void mapsRepresentativeImagesByPostId() {
    // given
    PostListRequest request = createListRequest(3);
    LocalDate referenceDate = LocalDate.of(2026, 11, 16);
    LocalDateTime now = referenceDate.atTime(10, 0);

    Post firstPost = createListPost(100L);
    Post secondPost = createListPost(99L);
    Post thirdPost = createListPost(98L);

    when(postRepository.findPosts(
        request, null, referenceDate, now
    )).thenReturn(List.of(firstPost, secondPost, thirdPost));

    when(postImageRepository.findRepresentativeImagesByPostIds(
        List.of(100L, 99L, 98L)
    )).thenReturn(List.of(
        representativeImage(99L, IMAGE_URL_2),
        representativeImage(100L, IMAGE_URL_1)
    ));

    // when
    PostListResponse response = postService.getPostList(
        request, null, referenceDate, now
    );

    // then
    assertEquals(
        List.of(100L, 99L, 98L),
        response.posts().stream().map(PostListItemResponse::postId).toList()
    );

    assertEquals(
        IMAGE_URL_1,
        response.posts().get(0).representativeImageUrl()
    );
    assertEquals(
        IMAGE_URL_2,
        response.posts().get(1).representativeImageUrl()
    );
    assertNull(response.posts().get(2).representativeImageUrl());

    assertFalse(response.hasNext());
    assertNull(response.nextCursor());

    verify(postImageRepository).findRepresentativeImagesByPostIds(
        List.of(100L, 99L, 98L)
    );
    verifyNoMoreInteractions(postImageRepository);
    verifyNoInteractions(postCursorFactory, postCursorCodec);
  }

  @Test
  @DisplayName("한 공고에 대표 이미지 중복 -> 예외 발생 및 커서 생성 중단")
  void rejectsDuplicateRepresentativeImages() {
    // given
    PostListRequest request = createListRequest(1);
    LocalDate referenceDate = LocalDate.of(2026, 11, 16);
    LocalDateTime now = referenceDate.atTime(10, 0);

    Post post = createListPost(100L);
    Post extraPost = createListPost(99L);

    when(postRepository.findPosts(
        request, null, referenceDate, now
    )).thenReturn(List.of(post, extraPost));

    when(postImageRepository.findRepresentativeImagesByPostIds(
        List.of(100L)
    )).thenReturn(List.of(
        representativeImage(100L, IMAGE_URL_1),
        representativeImage(100L, IMAGE_URL_2)
    ));

    // when
    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> postService.getPostList(
            request, null, referenceDate, now
        )
    );

    // then
    assertEquals(
        "공고에 대표 이미지가 여러 개 존재합니다. postId=100",
        exception.getMessage()
    );

    verify(postImageRepository).findRepresentativeImagesByPostIds(
        List.of(100L)
    );
    verifyNoMoreInteractions(postImageRepository);
    verifyNoInteractions(postCursorFactory, postCursorCodec);
  }

  @Test
  @DisplayName("종료된 공고 조회 -> 목록 필드와 종료 정보를 유지하고 시각을 +09:00으로 변환")
  void mapsClosedPostFieldsAndResponseTimes() {
    // given
    PostListRequest request = new PostListRequest(
        null,
        null,
        null,
        null,
        null,
        PostStatusFilter.ALL,
        null,
        null,
        10,
        null,
        null,
        null
    );

    LocalDate referenceDate = LocalDate.of(2026, 11, 16);
    LocalDateTime now = referenceDate.atTime(10, 0);

    Post post = createListPost(100L);
    LocalDateTime deadlineUtc = LocalDateTime.of(2026, 11, 15, 18, 0);
    LocalDateTime closedAtUtc = LocalDateTime.of(2026, 11, 16, 1, 30);

    ReflectionTestUtils.setField(
        post, "recruitmentDeadline", deadlineUtc
    );
    ReflectionTestUtils.setField(post, "bookmarkCount", 7);

    post.closeAfterDeadline(
        PostClosedReason.NO_APPLICANTS,
        closedAtUtc
    );

    when(postRepository.findPosts(
        request, null, referenceDate, now
    )).thenReturn(List.of(post));

    when(postImageRepository.findRepresentativeImagesByPostIds(
        List.of(100L)
    )).thenReturn(List.of(
        representativeImage(100L, IMAGE_URL_1)
    ));

    // when
    PostListResponse response = postService.getPostList(
        request, null, referenceDate, now
    );

    // then
    assertEquals(1, response.posts().size());
    PostListItemResponse item = response.posts().getFirst();

    assertAll(
        () -> assertEquals(Long.valueOf(100L), item.postId()),
        () -> assertEquals("공고 100", item.title()),
        () -> assertEquals(NoticeType.ADOPTION, item.noticeType()),
        () -> assertEquals(Species.CAT, item.species()),
        () -> assertEquals("코리안 숏헤어", item.breed()),
        () -> assertEquals(
            LocalDate.of(2026, 5, 1), item.estimatedBirthDate()
        ),
        () -> assertEquals(Gender.FEMALE, item.gender()),
        () -> assertEquals(Region.GYEONGGI, item.region()),
        () -> assertEquals("구리시", item.district()),
        () -> assertEquals(IMAGE_URL_1, item.representativeImageUrl()),
        () -> assertEquals(PostStatus.CLOSED, item.status()),
        () -> assertEquals(7, item.bookmarkCount()),
        () -> assertEquals(
            PostClosedReason.NO_APPLICANTS, item.closedReason()
        ),
        () -> assertEquals(
            OffsetDateTime.parse("2026-11-01T12:00:00+09:00"),
            item.createdAt()
        ),
        () -> assertEquals(
            OffsetDateTime.parse("2026-11-16T03:00:00+09:00"),
            item.recruitmentDeadline()
        ),
        () -> assertEquals(
            OffsetDateTime.parse("2026-11-16T10:30:00+09:00"),
            item.closedAt()
        )
    );

    assertEquals(deadlineUtc, post.getRecruitmentDeadline());
    assertEquals(closedAtUtc, post.getClosedAt());

    assertFalse(response.hasNext());
    assertNull(response.nextCursor());
    verifyNoInteractions(postCursorFactory, postCursorCodec);
  }

  private Member prepareAuthor(MemberStatus status, MemberRole role) {
    Member member = mock(Member.class);

    when(member.getMemberId()).thenReturn(MEMBER_ID);
    when(member.getStatus()).thenReturn(status);
    when(member.getRole()).thenReturn(role);
    when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

    return member;
  }

  private ImageUpload preparePendingUpload(Member uploader) {
    ImageUpload upload =
        new ImageUpload(uploader, "posts/1/image1.png", IMAGE_URL_1);

    when(imageUploadRepository.findAllByImageUrlInForUpdate(List.of(IMAGE_URL_1)))
        .thenReturn(List.of(upload));

    return upload;
  }

  private Post prepareSavedPost() {
    Post savedPost = mock(Post.class);

    when(savedPost.getPostId()).thenReturn(POST_ID);
    when(postRepository.save(any(Post.class))).thenReturn(savedPost);

    return savedPost;
  }

  private CreatePostRequest validRequest() {
    return createRequest(
        null,
        List.of(new PostImageRequest(IMAGE_URL_1, true, 0))
    );
  }

  private CreatePostRequest createRequest(
      OffsetDateTime deadline,
      List<PostImageRequest> images
  ) {
    return new CreatePostRequest(
        "구조한 고양이의 가족을 찾습니다.",
        "공고 등록 서비스 테스트입니다.",
        NoticeType.ADOPTION,
        Species.CAT,
        "코리안 숏헤어",
        null,
        Gender.UNKNOWN,
        false,
        null,
        "인창동 공원",
        Region.GYEONGGI,
        "구리시",
        deadline,
        images
    );
  }

  private void assertRejected(
      CreatePostRequest request,
      CommonErrorCode expectedCode
  ) {
    // when
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> postService.createPost(MEMBER_ID, request)
    );

    // then
    assertEquals(expectedCode, exception.getErrorCode());
    verifyNoInteractions(postRepository, postImageRepository);
  }

  private static Stream<Arguments> validRegionCases() {
    return Stream.of(
        Arguments.of(Region.SEOUL, "강남구", "강남구"),
        Arguments.of(Region.GYEONGGI, "  구리시  ", "구리시"),
        Arguments.of(Region.GYEONGGI, null, null),
        Arguments.of(Region.SEJONG, null, null),
        Arguments.of(Region.JEONNAM_GWANGJU, "광산구", "광산구"),
        Arguments.of(Region.JEONNAM_GWANGJU, "목포시", "목포시"),
        Arguments.of(Region.INCHEON, "서해구", "서해구")
    );
  }

  private static Stream<Arguments> invalidRegionCases() {
    return Stream.of(
        Arguments.of(null, null),
        Arguments.of(null, "강남구"),
        Arguments.of(Region.SEOUL, ""),
        Arguments.of(Region.SEOUL, "   "),
        Arguments.of(Region.SEOUL, "구리시"),
        Arguments.of(Region.GYEONGGI, "존재하지않는시"),
        Arguments.of(Region.SEJONG, "세종시"),
        Arguments.of(Region.INCHEON, "서구")
    );
  }

  private CreatePostRequest createRequestWithRegion(
      Region region,
      String district
  ) {
    CreatePostRequest base = validRequest();
    return new CreatePostRequest(
        base.title(),
        base.content(),
        base.noticeType(),
        base.species(),
        base.breed(),
        base.estimatedBirthDate(),
        base.gender(),
        base.isNeutered(),
        base.fosterPeriod(),
        base.foundLocation(),
        region,
        district,
        base.recruitmentDeadline(),
        base.images()
    );
  }

  private PostListRequest createListRequest(int size) {
    return new PostListRequest(
        null, // noticeType
        null, // species
        null, // gender
        null, // region
        null, // district
        null, // status: 기본값 적용
        null, // sort: 기본값 적용
        null, // cursor
        size,
        null, // minAgeMonths
        null, // maxAgeMonths
        null  // keyword
    );
  }

  private Post createListPost(Long postId) {
    Member author = new Member(
        "list-author@example.com",
        "encodedPassword",
        "목록테스트보호소",
        null,
        null
    );

    Post post = new Post(
        author,
        "공고 " + postId,
        "공고 목록 조회 테스트입니다.",
        NoticeType.ADOPTION,
        Species.CAT,
        "코리안 숏헤어",
        LocalDate.of(2026, 5, 1),
        Gender.FEMALE,
        true,
        null,
        "인창동 공원",
        Region.GYEONGGI,
        "구리시",
        null
    );

    ReflectionTestUtils.setField(post, "postId", postId);
    ReflectionTestUtils.setField(
        post,
        "createdAt",
        LocalDateTime.of(2026, 11, 1, 3, 0)
    );

    return post;
  }

  private RepresentativeImageView representativeImage(
      Long postId,
      String imageUrl
  ) {
    return new RepresentativeImageView() {
      @Override
      public Long getPostId() {
        return postId;
      }

      @Override
      public String getImageUrl() {
        return imageUrl;
      }
    };
  }
}