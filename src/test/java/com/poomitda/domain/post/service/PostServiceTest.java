package com.poomitda.domain.post.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.entity.constant.*;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.domain.post.dto.PostRequestDto.*;
import com.poomitda.domain.post.dto.PostResponseDto.CreatePostResponse;
import com.poomitda.domain.post.entity.*;
import com.poomitda.domain.post.entity.constant.*;
import com.poomitda.domain.post.repository.*;
import com.poomitda.domain.post.validation.PostCreateValidator;
import com.poomitda.global.constant.*;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class PostServiceTest {

  private final MemberRepository memberRepository = mock(MemberRepository.class);

  private final ImageUploadRepository imageUploadRepository = mock(ImageUploadRepository.class);

  private final PostRepository postRepository = mock(PostRepository.class);

  private final PostImageRepository postImageRepository = mock(PostImageRepository.class);

  private final PostService service = new PostService(
      memberRepository,
      imageUploadRepository,
      postRepository,
      postImageRepository,
      new PostCreateValidator()
  );

  private static final Long MEMBER_ID = 1L;
  private static final Long POST_ID = 100L;

  private static final String IMAGE_URL_1 = "https://example.com/posts/image1.png";

  private static final String IMAGE_URL_2 = "https://example.com/posts/image2.png";

  @Test
  @DisplayName("정상 공고 등록 -> 공고 및 이미지 저장과 업로드 이력 연결")
  void createsPostAndAttachesImages() {
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

    CreatePostResponse response = service.createPost(MEMBER_ID, request);

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
    Member author = prepareAuthor(
        MemberStatus.ACTIVE,
        MemberRole.ROLE_PROTECTOR
    );
    preparePendingUpload(author);
    prepareSavedPost();

    service.createPost(MEMBER_ID, validRequest());

    ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
    verify(postRepository).save(captor.capture());

    assertNull(captor.getValue().getRecruitmentDeadline());
  }

  @Test
  @DisplayName("입력값 조합 오류 -> DB 조회 및 저장 중단")
  void rejectsInvalidInputBeforeDatabaseAccess() {
    CreatePostRequest request = createRequest(
        null,
        List.of(new PostImageRequest(IMAGE_URL_1, false, 0))
    );

    assertRejected(request, CommonErrorCode.INVALID_INPUT_VALUE);

    verifyNoInteractions(memberRepository, imageUploadRepository);
  }

  @Test
  @DisplayName("회원 없음 -> 인증 오류 및 저장 중단")
  void rejectsMissingMember() {
    when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.empty());

    assertRejected(validRequest(), CommonErrorCode.UNAUTHORIZED);

    verifyNoInteractions(imageUploadRepository);
  }

  @Test
  @DisplayName("탈퇴한 회원 -> 인증 오류 및 저장 중단")
  void rejectsDeletedMember() {
    prepareAuthor(MemberStatus.DELETED, MemberRole.ROLE_PROTECTOR);

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
    prepareAuthor(MemberStatus.ACTIVE, role);

    assertRejected(validRequest(), CommonErrorCode.FORBIDDEN);

    verifyNoInteractions(imageUploadRepository);
  }

  @Test
  @DisplayName("업로드 이력 누락 -> 입력값 오류 및 저장 중단")
  void rejectsMissingUpload() {
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

    assertRejected(request, CommonErrorCode.INVALID_INPUT_VALUE);

    assertEquals(ImageUploadStatus.PENDING, upload.getStatus());
  }

  @Test
  @DisplayName("다른 회원의 이미지 사용 -> 입력값 오류 및 저장 중단")
  void rejectsAnotherMembersUpload() {
    prepareAuthor(MemberStatus.ACTIVE, MemberRole.ROLE_PROTECTOR);

    Member otherMember = mock(Member.class);
    when(otherMember.getMemberId()).thenReturn(2L);

    ImageUpload upload = preparePendingUpload(otherMember);

    assertRejected(validRequest(), CommonErrorCode.INVALID_INPUT_VALUE);

    assertEquals(ImageUploadStatus.PENDING, upload.getStatus());
  }

  @Test
  @DisplayName("이미 연결된 이미지 사용 -> 입력값 오류 및 저장 중단")
  void rejectsAttachedUpload() {
    Member author = prepareAuthor(
        MemberStatus.ACTIVE,
        MemberRole.ROLE_PROTECTOR
    );

    ImageUpload upload = preparePendingUpload(author);
    upload.attachToPost();

    assertRejected(validRequest(), CommonErrorCode.INVALID_INPUT_VALUE);

    assertEquals(ImageUploadStatus.ATTACHED, upload.getStatus());
  }

  @Test
  @DisplayName("공고 이미지 저장 실패 -> 기존 예외 전달 및 상태 전환 중단")
  void propagatesImageSaveFailure() {
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

    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> service.createPost(MEMBER_ID, validRequest())
    );

    assertSame(failure, exception);
    assertEquals(ImageUploadStatus.PENDING, upload.getStatus());
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
    BusinessException exception = assertThrows(
        BusinessException.class,
        () -> service.createPost(MEMBER_ID, request)
    );

    assertEquals(expectedCode, exception.getErrorCode());
    verifyNoInteractions(postRepository, postImageRepository);
  }
}