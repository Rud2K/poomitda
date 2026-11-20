package com.poomitda.domain.matching.service;

import com.poomitda.domain.matching.cursor.*;
import com.poomitda.domain.matching.dto.ApplicationRequestDto.PostApplicationListRequest;
import com.poomitda.domain.matching.dto.ApplicationResponseDto;
import com.poomitda.domain.matching.dto.ApplicationResponseDto.*;
import com.poomitda.domain.matching.entity.Application;
import com.poomitda.domain.matching.repository.ApplicationRepository;
import com.poomitda.domain.member.entity.Member;
import com.poomitda.domain.member.entity.constant.*;
import com.poomitda.domain.member.repository.MemberRepository;
import com.poomitda.domain.post.entity.Post;
import com.poomitda.domain.post.exception.PostErrorCode;
import com.poomitda.domain.post.repository.PostRepository;
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
public class PostApplicationListService {

  private final MemberRepository memberRepository;
  private final PostRepository postRepository;
  private final ApplicationRepository applicationRepository;
  private final PostApplicationCursorCodec postApplicationCursorCodec;
  private final PostApplicationCursorValidator postApplicationCursorValidator;
  private final PostApplicationCursorFactory postApplicationCursorFactory;

  /**
   * 현재 회원이 작성한 공고에 접수된 신청을 최신순으로 조회한다.
   *
   * @param memberId 현재 로그인한 회원 ID
   * @param request 공고 ID, 신청 상태, 커서 및 조회 개수를 담은 요청
   * @return 신청 목록과 다음 페이지 정보
   * @throws IllegalArgumentException 요청이 null인 경우
   * @throws BusinessException 인증, 권한, 요청 값 또는 커서가 유효하지 않거나 공고가 존재하지 않는 경우
   */
  @Transactional(readOnly = true)
  public ApplicationResponseDto.PostApplicationListResponse getPostApplications(
      Long memberId,
      PostApplicationListRequest request
  ) {
    // 요청 객체 확인
    if (request == null) {
      throw new IllegalArgumentException("공고별 신청 목록 조회 요청은 null일 수 없습니다.");
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

    // 공고 ID와 조회 개수 검증
    Long postId = request.postId();

    if (postId == null || postId <= 0) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    int size = request.size();

    if (size < 1 || size > 50) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    // 삭제된 공고도 포함해 조회
    Post post = postRepository.findByPostId(postId)
        .orElseThrow(
            () -> new BusinessException(PostErrorCode.POST_NOT_FOUND)
        );

    // 현재 요청자가 공고 작성자인지 확인
    if (!post.getAuthor().getMemberId().equals(memberId)) {
      throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }

    // 전달된 커서 복원 및 검증
    PostApplicationCursor cursor = null;

    if (request.cursor() != null) {
      cursor = postApplicationCursorCodec.decode(request.cursor());
      postApplicationCursorValidator.validate(cursor, memberId, request);
    }

    // 조회 시작 위치 준비
    LocalDateTime cursorCreatedAt = cursor == null
        ? null
        : cursor.createdAt();

    Long cursorApplicationId = cursor == null
        ? null
        : cursor.applicationId();

    // 다음 페이지 확인을 위해 한 건 더 조회
    List<Application> fetchedApplications = applicationRepository.findPostApplications(
        postId,
        request.status(),
        cursorCreatedAt,
        cursorApplicationId,
        PageRequest.of(0, size + 1)
    );

    // 다음 페이지 존재 여부와 실제 반환 범위 결정
    boolean hasNext = fetchedApplications.size() > size;

    List<Application> applications = hasNext
        ? fetchedApplications.subList(0, size)
        : fetchedApplications;

    // 실제 반환할 신청을 응답 항목으로 변환
    List<PostApplicationListItemResponse> responses = new ArrayList<>();

    for (Application application : applications) {
      PostApplicationListItemResponse response = toListItemResponse(application);
      responses.add(response);
    }

    // 다음 페이지 커서 생성
    String nextCursor = null;

    if (hasNext) {
      Application lastApplication = applications.getLast();

      PostApplicationCursor next = postApplicationCursorFactory.create(
          lastApplication,
          memberId,
          request
      );

      nextCursor = postApplicationCursorCodec.encode(next);
    }

    // 필요한 정보들을 담은 응답 반환
    return new PostApplicationListResponse(
        responses,
        nextCursor,
        hasNext
    );
  }

  /**
   * 신청 한 건을 신청자 요약과 처리 정보를 포함한 목록 항목으로 변환한다.
   *
   * @param application 응답으로 변환할 신청
   * @return 신청 목록 항목
   */
  private ApplicationResponseDto.PostApplicationListItemResponse toListItemResponse(
      Application application
  ) {
    // 신청자 준비
    Member applicant = application.getApplicant();

    // 신청자 요약 응답 생성 후 반환
    ApplicationApplicantResponse applicantResponse = new ApplicationApplicantResponse(
        applicant.getMemberId(),
        applicant.getNickname()
    );

    return new PostApplicationListItemResponse(
        application.getApplicationId(),
        application.getStatus(),
        applicantResponse,
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
