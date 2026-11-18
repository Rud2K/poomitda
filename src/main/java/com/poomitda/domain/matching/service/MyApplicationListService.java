package com.poomitda.domain.matching.service;

import com.poomitda.domain.matching.cursor.*;
import com.poomitda.domain.matching.dto.ApplicationRequestDto.MyApplicationListRequest;
import com.poomitda.domain.matching.dto.ApplicationResponseDto;
import com.poomitda.domain.matching.dto.ApplicationResponseDto.*;
import com.poomitda.domain.matching.entity.Application;
import com.poomitda.domain.matching.repository.ApplicationRepository;
import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.entity.constant.*;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.domain.post.entity.Post;
import com.poomitda.domain.post.repository.PostImageRepository;
import com.poomitda.domain.post.repository.PostImageRepository.RepresentativeImageView;
import com.poomitda.global.exception.CommonErrorCode;
import com.poomitda.global.exception.exception.BusinessException;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyApplicationListService {

  private final MemberRepository memberRepository;
  private final ApplicationRepository applicationRepository;
  private final PostImageRepository postImageRepository;
  private final MyApplicationCursorCodec myApplicationCursorCodec;
  private final MyApplicationCursorValidator myApplicationCursorValidator;
  private final MyApplicationCursorFactory myApplicationCursorFactory;

  /**
   * 현재 회원이 작성한 신청을 최신순으로 조회한다.
   *
   * @param memberId 현재 로그인한 회원 ID
   * @param request 신청 상태, 커서 및 조회 개수를 담을 요청
   * @return 신청 목록과 다음 페이지 정보
   * @throws IllegalArgumentException 요청이 null인 경우
   * @throws BusinessException 인증, 권한, 조회 개수 또는 커서가 유효하지 않은 경우
   */
  @Transactional(readOnly = true)
  public ApplicationResponseDto.MyApplicationListResponse getMyApplications(
      Long memberId,
      MyApplicationListRequest request
  ) {
    // 요청 객체 확인
    if (request == null) {
      throw new IllegalArgumentException("내 신청 목록 조회 요청은 null일 수 없습니다.");
    }

    // 현재 요청자의 인증 정보 확인
    if (memberId == null) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }

    Member currentMember = memberRepository.findById(memberId)
        .orElseThrow(
            () -> new BusinessException(CommonErrorCode.UNAUTHORIZED)
        );

    if (currentMember.getStatus() != MemberStatus.ACTIVE) {
      throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
    }

    // 현재 요청자의 역할 확인
    MemberRole role = currentMember.getRole();

    if (role != MemberRole.ROLE_USER && role != MemberRole.ROLE_PROTECTOR) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    // 조회 개수 검증
    int size = request.size();

    if (size < 1 || size > 50) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    // 첫 페이지의 커서 초기화
    MyApplicationCursor cursor = null;

    // 커서가 전달된 경우 복원 후 검증
    if (request.cursor() != null) {
      cursor = myApplicationCursorCodec.decode(request.cursor());
      myApplicationCursorValidator.validate(cursor, memberId, request);
    }

    // 조회 시작 위치 준비
    LocalDateTime cursorCreatedAt = cursor == null ? null : cursor.createdAt();
    Long cursorApplicationId = cursor == null ? null : cursor.applicationId();

    // 다음 페이지 확인을 위해 한 건 더 조회
    List<Application> fetchedApplications = applicationRepository.findMyApplications(
        memberId,
        request.status(),
        cursorCreatedAt,
        cursorApplicationId,
        PageRequest.of(0, size + 1)
    );

    // 다음 페이지 존재 여부와 실제 반환 범위 결정
    boolean hasNext = fetchedApplications.size() > size;
    List<Application> applications = hasNext ? fetchedApplications.subList(0, size) : fetchedApplications;

    // 실제 반환할 신청들의 대표 이미지 일괄 조회
    Map<Long, String> imageUrls = getRepresentativeImageUrls(applications);

    // 목록 응답 항목 생성
    List<MyApplicationListItemResponse> responses = new ArrayList<>();

    for (Application application : applications) {
      String representativeImageUrl = imageUrls.get(application.getPost().getPostId());

      MyApplicationListItemResponse response = toListItemResponse(
          application,
          representativeImageUrl
      );

      responses.add(response);
    }

    // 다음 페이지 커서 생성
    String nextCursor = null;

    if (hasNext) {
      Application lastApplication = applications.getLast();

      MyApplicationCursor next = myApplicationCursorFactory.create(
          lastApplication,
          memberId,
          request
      );

      nextCursor = myApplicationCursorCodec.encode(next);
    }

    // MyApplicationListResponse에 필요한 정보를 담아 반환
    return new MyApplicationListResponse(
        responses,
        nextCursor,
        hasNext
    );
  }

  /**
   * 반환할 신청 목록에 연결된 공고들의 대표 이미지 URL을 한 번에 조회한다.
   *
   * @param applications 실제 응답에 포함할 신청 목록, null이 아니어야 한다.
   * @return 공고 ID를 키로 하는 대표 이미지 URL Map
   * @throws IllegalStateException 동일 공고에 대표 이미지가 여러 개 존재하는 경우
   */
  private Map<Long, String> getRepresentativeImageUrls(List<Application> applications) {
    // 반환할 신청이 없으면 이미지 조회 없이 빈 Map 반환
    if (applications.isEmpty()) {
      return Map.of();
    }

    // 조회할 공고 ID 모으기
    Set<Long> postIds = new HashSet<>();

    for (Application application : applications) {
      postIds.add(application.getPost().getPostId());
    }

    // 대표 이미지 일괄 조회
    List<RepresentativeImageView> images = postImageRepository.findRepresentativeImagesByPostIds(postIds);

    // 공고 ID별 이미지 URL을 담을 Map 준비
    Map<Long, String> imageUrls = new HashMap<>();

    // 조회된 이미지를 순회하며 Map 구성
    for (RepresentativeImageView image : images) {
      Long postId = image.getPostId();

      if (imageUrls.containsKey(postId)) {
        throw new IllegalStateException("동일 공고에 대표 이미지가 여러 개 존재합니다.");
      }

      imageUrls.put(postId, image.getImageUrl());
    }

    // 완성된 Map 반환
    return imageUrls;
  }

  /**
   * 신청 한 건을 공고 요약과 처리 정보를 포함한 목록 항목으로 변환한다.
   *
   * @param application 응답으로 변환할 신청
   * @param representativeImageUrl 대표 이미지 URL, 이미지가 없으면 null
   * @return 신청 목록 항목
   */
  private ApplicationResponseDto.MyApplicationListItemResponse toListItemResponse(
      Application application,
      String representativeImageUrl
  ) {
    // 연결된 공고 준비
    Post post = application.getPost();

    // 공고 요약 응답 생성
    ApplicationListPostResponse postResponse = new ApplicationListPostResponse(
        post.getPostId(),
        post.getTitle(),
        post.getNoticeType(),
        post.getSpecies(),
        post.getStatus(),
        representativeImageUrl
    );

    // 신청 목록 항목 생성 후 반환
    return new MyApplicationListItemResponse(
        application.getApplicationId(),
        application.getStatus(),
        postResponse,
        toResponseTime(application.getCreatedAt()),
        toResponseTime(application.getUpdatedAt()),
        toResponseTime(application.getApprovedAt()),
        toResponseTime(application.getCompletedAt()),
        application.getCompletionMethod(),
        application.getRejectionReason(),
        toResponseTime(application.getRejectedAt())
    );
  }

  /**
   * UTC 기준 시각을 동일한 순간의 한국 시각으로 변환한다.
   *
   * @param dateTime UTC 기준 시각, null 가능
   * @return +09:00으로 변환한 시각, 입력이 null이면 null
   */
  private OffsetDateTime toResponseTime(LocalDateTime dateTime) {
    return dateTime == null
        ? null
        : dateTime.atOffset(ZoneOffset.UTC).withOffsetSameInstant(ZoneOffset.ofHours(9));
  }
}
